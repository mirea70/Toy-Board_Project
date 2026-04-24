# Toy Board — 성능 실험 기록

모놀리스 → 스케일아웃 → MSA 전환 과정에서 아키텍처와 환경을 바꿔가며 측정한 결과.
각 stage 의 환경은 `env.md`, 관찰 노트는 `notes.md` 에 기록되고 아래 요약표에서 한눈에 비교할 수 있습니다.

- **실험 실행 가이드**: [`RUN.md`](RUN.md)
- 부하 시나리오 근거: [`../k6/METHODOLOGY.md`](../k6/METHODOLOGY.md) (NN/g 90-9-1)
- 부하 스크립트: [`../k6/board-load.js`](../k6/board-load.js)
- 관찰 노트 템플릿: [`TEMPLATE.md`](TEMPLATE.md)

---

## 실험 결과 요약

> **RPS / p95 / CPU** 등 초당 지표는 방법론 전환 전후로 비교 가능하지만
> **VU 숫자는 비교 불가** (압축 시나리오 VU N ≈ 현실 시나리오 VU 6N —
> [METHODOLOGY.md §4-4](../k6/METHODOLOGY.md) 참조).

### [A] 압축 시나리오 (stage0~2, iter_dur ~4.6s)

초기 탐색 단계 — 적은 VU 로 시스템 압박을 빠르게 드러내기 위한 "compressed load".
설계 근거: [METHODOLOGY.md §4-2](../k6/METHODOLOGY.md).

| stage | 아키텍처 | cpus | VU | 한계 RPS (p95<500ms) | p50 | p95 | p99 | 주 병목 | 상세 |
|---|---|---|---|---|---|---|---|---|---|
| 0 | 모노 ×1 | 0.5 | 50 | ≥29 (한계 미도달) | 6.5ms | 99ms | ~760ms | CPU 65%, GC 단조증가 | [stage0](stage0/) |
| 1 | 모노 ×1 | 1.0 | 100 | ≥56 (한계 미도달) | 2.7ms | 19ms | 46ms (spike 195ms) | 없음 — CPU 50%, Hikari active max 1 | [stage1](stage1/) |
| 2 | 모노 ×1 | 1.0 | 200 | ≥109 (곡선 꺾임 시작) | 2.9ms | 91ms | 313ms (sustained) | CPU 81% + Hikari pending 5 (첫 신호) | [stage2](stage2/) |

### [B] 현실 시나리오 (stage3+, iter_dur ~27s)

sleep 값을 실제 게시판 체감 기준(8~15s / 3~8s / 5~15s)으로 전환. VU 숫자가 실제
"접속 중 사용자 수"에 가깝게 해석됩니다. 설계 근거: [METHODOLOGY.md §4-3](../k6/METHODOLOGY.md).

| stage | 아키텍처 | cpus | VU | 한계 RPS (p95<500ms) | p50 | p95 | p99 | 주 병목 | 상세 |
|---|---|---|---|---|---|---|---|---|---|
| 3 | 모노 ×1 | 1.0 | 800 | ≥88 | 2.7ms | 19ms | ~100ms / 850ms (ramp) | 없음 — CPU mean 20%, Hikari pending 0 | [stage3](stage3/) |
| 4 | 모노 ×1 | 1.0 | 1500 | ≥160 (첫 실패 3건) | 2.9ms | 89ms | mean 161ms / max 341ms | Hikari pending 16 + GC 9.16ms/s (CPU 70% 여유) | [stage4](stage4/) |
| 5 | 모노 ×1, Hikari 30 | 1.0 | 1500 | ≥160 | 2.8ms | **29ms** | mean 116ms / max 503ms (single spike) | latency ← pool 해소 확정, RPS ← CPU 70.8% | [stage5](stage5/) |
| 6 | 모노 ×1, Hikari 30 | 1.0 | 3000 | **≈306** ❌ threshold 일부 실패 | 8.6ms | **507ms** | mean 627ms / max 1.83s | 전방위 포화 — CPU 100%, pool pending 25, GC 17ms/s | [stage6](stage6/) |
| 7 | 모노 ×2 + nginx | 1.0 ×2 | 3000 | ≥310 | 3.2ms | **16.5ms** | mean 39ms / max 386ms | 없음 — CPU 각 37%, Hikari active 3~4 | [stage7](stage7/) |
| 8 | 모노 ×2 + nginx | 1.0 ×2 | 6000 | ≥604 (선형 확장) | 4.3ms | 59ms | mean 110ms / max 757ms | CPU 재포화 임박(99.8% / 93.8%). 읽기가 요청 96.7% / 서버시간 89.7% 점유 → 읽기 분리 정당성 확보 | [stage8](stage8/) |
| 9 | read / write 분리, read ×2, Kafka + Outbox + cache prepopulation | 1.0×3 | 6000 (7.5분) | **≥550** | 2.79ms | **19.1ms** | max 757ms | 분리 효과 검증(H1 ✓). cold-start / JS-precision / ZSET-miss / N+1 4종 fix 후 stage8 대비 p95 **−68%** | [stage9](stage9/) |
| 10 | + read 측 comment count 캐싱 (sliding TTL 1일, 이벤트 갱신, cold miss 만 write 호출) | 1.0×3 | 6000 | **≥549** | 1.23ms | **5.47ms** | mean 12.9ms / max **21.3ms** | 없음 — write CPU 30.7→**11.88%** (−61%), `/count` 호출 99%+ 감소 | [stage10](stage10/) |
| 11 | + `readAll` viewCount 배치화 (bulk 엔드포인트, 캐시 TTL 1s) | 1.0×3 | 6000 | ≥551 | 0.98ms | 4.54ms | mean 17.0ms / **max 69.7ms** | **회귀 → 롤백**. write CPU +5.66%p, `/articles` 요청당 +39%. 직렬화 비용 + cold start + stampede 미방어 | [stage11](stage11/) |
| 12 | stage10 동일 + **k6 외부 Mac 이동** (WiFi 동일 subnet) + **VU 6000 → 12000** + observability 보강 | 1.0×3 | **12000** | **≥961** ❌ 실패율 임계 위반 | 1.30ms | 56.8ms | mean 194ms / **max 1.49s** | read-app CPU max 100%, Tomcat busy max 200. I/O wait 기각 (redis rejected=0, HTTP mean 0.015) → 병목 = **read-app CPU + Tomcat thread pool**. 실패율 2.53% | [stage12](stage12/) |
| 13 | read 도메인 분리 — `common:read-common` + `article-read`(`/v1/articles*`) + `sub-read`(`/v1/hot-articles*`, `/v2/comments*`). nginx path 기반 3-way, 독립 consumer group | 1.0×3 | 12000 | ≈909 (mean, 합산) | 1.46ms | **40.4ms** | mean **119ms** / max **559ms** | p99 mean −39% / max −62%, Tomcat 포화 단발(1회), Kafka lag 0 유지. **article-read 주 병목 지속** (CPU max 100%), sub-read 44.78% / write 25.52% 여유 | [stage13](stage13/) |
| 14 | stage13 동일 구성 + **서버 호스트 Win → macOS 이동** + **k6 를 Win 데스크탑으로 외부화** (호스트 CPU 포화 변수 제거) + `board-load.js` 데드락 수정 (`create article` 을 `if (lastArticleId)` 밖으로) | 1.0×3 | 12000 | **952 req/s** (k6), 서버 여유 | 1.52ms | **7.71ms** | mean **19.9ms** / max **102ms** | 서버 전 구간 여유 (article-read CPU 46.79/78.20%, Tomcat busy max 18). stage13 의 article-read 포화는 **호스트 CPU 경쟁 artifact** 로 판명. 병목이 **네트워크 계층**으로 이동 — 실패율 2.93% ❌, `http_req_connecting` max 15s, iteration 초기 list/hot 에 실패 집중 | [stage14](stage14/) |

---

## 실험 표준 절차

상세 단계는 [`RUN.md`](RUN.md) 참조. 핵심 순서:

1. **stage 폴더 생성** — `TEMPLATE.md` → `notes.md`, 이전 stage 의 `env.md` 복사
2. **`env.md` 수정** — 헤더 / 리소스 / 시나리오 / 이전 대비 변경점 갱신
3. **스택 기동** — `docker compose up -d --build`, Prometheus 타겟 UP 확인
4. **smoke → 본 부하** — `k6 run ... --summary-export=... | tee ...`
5. **Grafana 스크린샷** — 필수 4종 + 상황별 추가
6. **`notes.md` 작성** — 한계 지표 / 병목 가설 / 놀라운 점 / 다음 계획
7. **이 `README.md` 요약표 갱신** — 해당 stage 행에 수치 기록

---

## 폴더 구조

```
perf/results/
├── README.md              ← 이 파일 (요약표)
├── RUN.md                 ← 실험 실행 가이드 (공통)
├── TEMPLATE.md            ← 관찰 노트 템플릿
└── stageN/                ← 각 stage 폴더 (동일 구조)
    ├── env.md             ← 환경 기록
    ├── notes.md           ← 관찰 코멘트
    ├── k6-summary.json    ← k6 --summary-export 결과
    ├── k6-console.txt     ← k6 실시간 로그
    └── grafana-*.png      ← 대시보드 스크린샷 (종류는 RUN.md §7)
```

재현성 체크리스트는 [`RUN.md`](RUN.md) 하단 참조.
