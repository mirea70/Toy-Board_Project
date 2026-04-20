import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';
import { randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

/**
 * Toy Board — mixed-workload scenario ("평상시 트래픽").
 *
 * 믹스 근거: Nielsen Norman Group, "The 90-9-1 Rule for Participation
 * Inequality" (2006) — https://www.nngroup.com/articles/participation-inequality/
 *
 *   Lurker 90% / Intermittent 9% / Heavy 1% 유저 분포를 세션당 행동으로
 *   환산하면 읽기 93% / 좋아요 1.9% / 댓글 0.2% / 글작성 0.06% 가 이론값.
 *   본 스크립트는 희소 쓰기 경로의 통계 샘플 확보를 위해 쓰기 비율을
 *   의도적으로 과대 표집했습니다. 전체 도출 과정과 조정 근거는
 *   perf/k6/METHODOLOGY.md 를 참조.
 *
 * 실제 구현 비율:
 *   - 읽기 (목록/인기글/상세/댓글)  : 93%
 *   - 좋아요 토글                   :  5%  (이론값 1.9%)
 *   - 댓글 작성                     : 1.5% (이론값 0.2%)
 *   - 글 작성                       : 0.5% (이론값 0.06%)
 *
 * Think-time (사용자가 글 읽는 시간):
 *   stage3 부터 **현실 게시판 유저 체감 기준** 으로 전환.
 *   - 목록 → 다음 행동  :   8~15s
 *   - 상세 → 댓글       :   3~8s
 *   - iter 종료 대기    :   5~15s
 *   → iteration_duration 평균 ~27s. stage0~2 의 "압축 시나리오(iter_dur ~4.6s)"
 *     와는 비교 불가. 변경 근거와 환산 규칙은 METHODOLOGY.md §4 참조.
 *
 * Env:
 *   BASE_URL       (default http://localhost:8080)
 *   BOARD_ID       (default 1)
 *   LIKE_STRATEGY  (default pessimistic-lock-1)
 *   MAX_USER_ID    (default 30000)
 */

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const BOARD_ID = Number(__ENV.BOARD_ID || 1);
const LIKE_STRATEGY = __ENV.LIKE_STRATEGY || 'pessimistic-lock-1';
const MAX_USER_ID = Number(__ENV.MAX_USER_ID || 30000);

const listTrend = new Trend('board_list_duration', true);
const readTrend = new Trend('board_read_duration', true);
const writeCounter = new Counter('board_writes');

export const options = {
  discardResponseBodies: false,
  scenarios: {
    browse: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '1m',  target: 200 },
        { duration: '4m',  target: 3000 },
        { duration: '11m', target: 3000 },
        { duration: '1m',  target: 0    },
      ],
      gracefulRampDown: '10s',
      exec: 'browse',
    },
  },
  thresholds: {
    http_req_failed:   ['rate<0.02'],
    http_req_duration: ['p(95)<500', 'p(99)<1500'],
    board_list_duration: ['p(95)<400'],
    board_read_duration: ['p(95)<400'],
  },
};

function ymd(d = new Date()) {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}${m}${day}`;
}

function pickArticleId(listJson) {
  const arr = listJson && listJson.articles;
  if (!Array.isArray(arr) || arr.length === 0) return null;
  const pick = arr[randomIntBetween(0, arr.length - 1)];
  return pick && pick.articleId;
}

export function browse() {
  const userId = randomIntBetween(1, MAX_USER_ID);
  let lastArticleId = null;

  group('list articles', () => {
    const page = randomIntBetween(1, 5);
    const res = http.get(`${BASE_URL}/v1/articles?boardId=${BOARD_ID}&page=${page}&pageSize=10`,
      { tags: { name: 'GET /v1/articles' } });
    check(res, { 'list 200': (r) => r.status === 200 });
    listTrend.add(res.timings.duration);
    try { lastArticleId = pickArticleId(res.json()); } catch (_) {}
  });

  sleep(randomIntBetween(8, 15));   // 목록 훑어보고 다음 행동까지

  group('hot articles', () => {
    if (Math.random() < 0.35) {
      const res = http.get(`${BASE_URL}/v1/hot-articles/articles/date/${ymd()}`,
        { tags: { name: 'GET /v1/hot-articles' } });
      check(res, { 'hot 200': (r) => r.status === 200 });
    }
  });

  if (lastArticleId) {
    group('read article', () => {
      const res = http.get(`${BASE_URL}/v1/articles/${lastArticleId}?userId=${userId}`,
        { tags: { name: 'GET /v1/articles/{id}' } });
      check(res, { 'read 200': (r) => r.status === 200 });
      readTrend.add(res.timings.duration);
    });

    sleep(randomIntBetween(3, 8));   // 글 본문 읽고 댓글 섹션 이동까지

    group('list comments', () => {
      const res = http.get(`${BASE_URL}/v2/comments?articleId=${lastArticleId}&page=1&pageSize=20`,
        { tags: { name: 'GET /v2/comments' } });
      check(res, { 'comments 200': (r) => r.status === 200 });
    });

    // Write-path mix per METHODOLOGY.md (NN/g 90-9-1, over-sampled for stats).
    // 0.000 ─ 0.050  toggle like     (5.0%)
    // 0.050 ─ 0.065  create comment  (1.5%)
    // 0.065 ─ 0.070  create article  (0.5%)
    // 0.070 ─ 1.000  read-only       (93.0%)
    const dice = Math.random();

    if (dice < 0.050) {
      group('toggle like', () => {
        const url = `${BASE_URL}/v1/article-likes/articles/${lastArticleId}/users/${userId}/${LIKE_STRATEGY}`;
        const likeRes = http.post(url, null, { tags: { name: 'POST /v1/article-likes' } });
        check(likeRes, { 'like ok': (r) => r.status >= 200 && r.status < 300 || r.status === 409 });
        writeCounter.add(1);
        sleep(0.5);
        const unlikeRes = http.del(url, null, { tags: { name: 'DELETE /v1/article-likes' } });
        check(unlikeRes, { 'unlike ok': (r) => r.status >= 200 && r.status < 300 || r.status === 404 });
        writeCounter.add(1);
      });
    } else if (dice < 0.065) {
      group('create comment', () => {
        const body = JSON.stringify({ articleId: lastArticleId, writerId: userId, content: `load-test ${Date.now()}` });
        const res = http.post(`${BASE_URL}/v2/comments`, body,
          { headers: { 'Content-Type': 'application/json' }, tags: { name: 'POST /v2/comments' } });
        check(res, { 'comment created': (r) => r.status >= 200 && r.status < 300 });
        writeCounter.add(1);
      });
    } else if (dice < 0.070) {
      group('create article', () => {
        const body = JSON.stringify({
          boardId: BOARD_ID,
          writerId: userId,
          title: `load-test ${Date.now()}`,
          content: 'generated by k6 board-load.js',
        });
        const res = http.post(`${BASE_URL}/v1/articles`, body,
          { headers: { 'Content-Type': 'application/json' }, tags: { name: 'POST /v1/articles' } });
        check(res, { 'article created': (r) => r.status >= 200 && r.status < 300 });
        writeCounter.add(1);
      });
    }
  }

  sleep(randomIntBetween(5, 15));   // iteration 종료 후 다음 세션 시작까지의 체류
}
