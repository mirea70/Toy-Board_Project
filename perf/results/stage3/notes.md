# stage3 — 관찰 노트

> **방법론 전환 지점**. VU 200 (압축 시나리오) → VU 800 (현실 시나리오,
> iter_dur 평균 27s). 변경 근거는 [`METHODOLOGY.md §4`](../../k6/METHODOLOGY.md).
> stage2 와의 VU 숫자 비교는 무의미, **RPS / p95 / CPU 축으로만 비교** 가능.

## 한 줄 요약

VU 800 (현실) 로 전환했더니 **stage2 (VU 200 압축) 보다 모든 지표가 확실히 더
편안**하게 나왔다.

- RPS 109 → 88 (LESS load), p95 91ms → 19ms (5배 ↓)

- HikariCP pending 5 → **0** (DB pool 여유 회복)

- **CPU max 81% → 62.8%**(23%p ↓)

## 한계 지표 (k6 summary)


| 항목 | 값 | 비고 |
|---|---|---|
| **한계 RPS** (p95 < 500ms 유지 기준) | ≥88 (한계 미도달) | threshold 모두 ✓ pass (k6 `✓` 마크 확인) |
| p50 | 2.67ms (http_req med) | |
| p95 (k6, 전체 run) | **19.04ms** | 목표 500ms 의 3.8% — 여유 많음 |
| p99 max (Grafana, 전체 run 중) | **850ms** | 람프 2 구간의 transient — hold 구간 한정 시 ~100ms |
| iterations 총합 | 22,765 (+492 interrupted) | 25.0 iter/s |
| iteration_duration 평균 | **27.01s** | 설계값과 일치 → 현실 시나리오 의도대로 동작 |
| http_reqs | 80,431 | **88.4 req/s** |
| 실패율 | 0.00% (0 / 80,431) | |
| k6 threshold | ✅ pass | `p(95)<500`, `p(99)<1500`, `board_*_duration p(95)<400`, `rate<0.02` |

## 리소스 사용 관찰 (Grafana)

> Time range 는 부하 시작 +3m 이후 (첫 2개 램프 제외) 로 잡았음. Legend
> mean/max/last 자동.

| 지표 | mean | max | last | 관찰 |
|---|---|---|---|---|
| `process_cpu_usage` | 20.55% | **62.80%** | 1.05% | stage2 max 81% 대비 **23%p 감소**. peak 는 14:59 경 (램프 2 끝~hold 초입) 에서 한 번, hold 안정 구간은 25~35% 수준 |
| `jvm_gc_pause_seconds` rate | 781 μs | 1.78 ms | 36 μs | stage2 max 3.71ms → stage3 max 1.78ms. **절반 이하로 감소** |
| `hikaricp_connections_active` | 0.158 | **5** | 0 | stage2 max 9 → stage3 max 5. 한 번도 pool 꽉 안 참 |
| `hikaricp_connections_idle` | 9.84 | 10 | 10 | |
| `hikaricp_connections_pending` | **0** | **0** | **0** | stage2 max 5 → stage3 **0** — DB pool 압박 **완전히 사라짐** |

### 엔드포인트별 p95 (Latency p95 by URI)

| URI | p95 mean | p95 max | 관찰 |
|---|---|---|---|
| `GET /v1/articles/{articleId}` | 4.48ms | 9.08ms | Redis 캐시, 여전히 가장 빠름 |
| `GET /v2/comments` | 9.35ms | 20.1ms | 안정적 |
| `GET /v1/hot-articles/.../dateStr` | 30.7ms | 54.0ms | stage2 mean 97.7 → **stage3 30.7**. 캐시 효율 회복 (stage2 의 의심점 해소) |
| `POST /v1/article-likes/.../pessimistic-lock-1` | 37.4ms | 78.3ms | stage2 mean 98.6 → 37.4. 쓰기 경로도 훨씬 빠름 |
| `GET /v1/articles` (list) | 340ms (mean 포함 램프 spike) | — | 램프 구간 spike 주범 (아래 참고) |

## stage2 대비 변화 (단, **시나리오가 다름** — RPS/p95/CPU 축만 유효)

| 항목 | stage2 (VU 200 압축) | stage3 (VU 800 현실) | 해석 |
|---|---|---|---|
| **RPS** | 109 | **88** | ↓ 19%. 현실 시나리오가 stage2 load 를 완전 재현하진 못함 (예측 109 대비 미달) |
| **p95 (k6)** | 91ms | **19ms** | 5배 ↓ |
| **p99 max (Grafana)** | 313ms (hold 중 sustained) | 850ms (ramp 중 transient) + ~100ms (hold) | 성격이 다름: stage2 는 "지속 고지연", stage3 는 "램프 쇼크 + 빠른 안정" |
| **CPU max** | 81% | **62.8%** | 23%p ↓. stage2 의 "hold 내내 고지속" 과 달리 stage3 는 단일 peak + 낮은 지속률 |
| **HikariCP pending max** | 5 | **0** | **DB pool 압박 완전 해소** |
| **GC pause max** | 3.71 ms/s | 1.78 ms/s | 절반 이하 |

## 병목 가설

**현재 부하(VU 800 현실) 에서는 다시 병목 없음 상태로 복귀.** 정확히 말하면:
- hold 구간의 지속 병목은 **없음** — pool idle 9~10, CPU mean 20%, GC 감소
- 단 **램프 구간에서 일회성 충격** 발생 — p99 850ms

### 왜 stage2 보다 편해졌는가?

Realistic think-time 의 효과:
- VU 200 (압축): 각 VU 가 4.6s 마다 3~4 요청을 **몰아서** 날림 → 순간 동시 DB 쿼리가 많이 쌓임 → pool pending 발생
- VU 800 (현실): 각 VU 가 27s 마다 3~4 요청을 **분산해서** 날림 → 순간 동시 DB 쿼리가 적게 유지 → pool 여유

**즉 같은 RPS 수준이어도 "요청 분산 패턴" 이 다르면 DB 병목 양상이 질적으로
달라진다.** 이는 아키텍처 자체 변경 없이 발생한 차이이므로, "현실 유저 분포
하에서 모놀리틱 ×1 이 800명을 얼마나 편안히 수용하는지" 의 baseline 으로 삼을
만함.

### 램프 transient 는 뭔가

14:54:30 ~ 14:55:30 구간 (램프 2 초반) 의 850ms spike 가설:
- (a) **급격한 VU 증가로 Tomcat thread pool 급증 요청** → 스레드 생성 비용 누적
- (b) **Warmed-but-unused 경로의 재 warmup** — stage3 시작 직전에 smoke 만 돌렸음. 실제 hot path 는 첫 램프에서 다시 warm 시작
- (c) **k6 클라이언트측 connection 급증 대응** — 200 → 800 짧은 시간, TCP/HTTP keep-alive pool 확장 비용

측정 방식상 해결법: 램프 2 를 2m → **3~4m** 로 늘려 충격 완화. 본질적 해결은
아니지만 "안정상태만" 측정하려면 유용.

## 놀라운 점 / 예상과 다른 점

1. **VU 4배로 늘렸는데 RPS 는 오히려 19% 감소 (109 → 88).** 예측(RPS 109) 대비
   미달. 원인은 iteration_duration avg 가 27s 로 설계대로였지만 **interrupted
   iterations 492건** (rampdown 에서 끊긴 iteration) 만큼 샘플 손실. 본질적
   이유는 아니고 측정 정확도의 잡음.

2. **`/v1/hot-articles` 가 stage2 (97.7ms) → stage3 (30.7ms) 로 ~3배 빨라짐.**
   stage2 에서 "Redis 캐시치고 예상외로 느림" 이라 적었던 의심점이 **부하 분산
   패턴의 차이** 로 설명됨. hot-articles 는 캐시 조회 자체는 빠르지만 stage2 의
   burst-like 접근이 Redis 연결 경합을 일으켰고, stage3 의 분산 접근에선 여유.

3. **CPU 가 peak/mean 양쪽 모두 대폭 감소 (81%→62.8%, 40%→20%).** 처음엔 "peak 은
   비슷하고 mean 만 감소할 것" 으로 예상했는데, 실제로는 peak 자체도 23%p 낮아짐.
   이는 같은 요청량이어도 **burst vs 분산 패턴의 CPU 소모량 자체가 다르다**는
   뜻 — burst 시에는 context switch, lock contention, scheduler overhead 같은
   간접 비용이 동시에 몰리며 실질 CPU 소비를 증폭시킨다고 해석 가능.

4. **GC pause 가 오히려 감소 (3.71ms → 1.78ms).** 할당률이 낮아졌다는 뜻 —
   VU 4배인데 할당률 감소는 반직관. 원인: stage2 의 burst 요청이 heap 에 짧은
   시간에 많은 임시 객체를 쌓았는데, stage3 의 분산 요청은 객체 생성-수거 사이
   간격이 길어 minor GC 만으로 처리 가능해진 것으로 추정.

5. **Transient ramp spike 가 "놓치기 쉬운" 실무 교훈.** 운영 환경에서 오토스케일링
   이 빠르게 VU 를 늘리면 이런 쇼크가 재현 가능. 운영 관점의 시사점이 있는
   발견.

## 다음 실험 계획

두 갈래로 갈 수 있음:

**(A) 모노 한계 더 밀어보기**
- VU 800 → **1500** 상향. 현실 시나리오 하에서 모놀리틱 ×1 의 실제 한계 탐색.

---

## 체크리스트

- [x] `env.md` 작성 (환경 고정)
- [ ] `k6-summary.json` 저장
- [x] `k6-console.txt` 저장 (stdout)
- [x] `grafana-overview.png` 저장
- [x] `grafana-latency.png` 저장
- [x] `grafana-hikari.png` 저장
- [x] `grafana-gc.png` 저장
- [x] `../README.md` 요약 표에 한 줄 갱신
