# stage2 — 관찰 노트

> CPU 1.0 유지, VU 100 → 200 상향. 좋아요 전략 `pessimistic-lock-2` →
> `pessimistic-lock-1` (원자적 UPDATE).
> 모놀리틱 단일 인스턴스의 실제 한계 탐색을 이어가는 실험.

## 한 줄 요약

VU 100 → 200 확장에서 **드디어 첫 병목 신호가 나타남.** RPS 는 56 → 109 로
여전히 선형 확장했지만, **HikariCP pending 최대 5 도달** (pool size 10 중 active
9, pool 거의 꽉 참) + **CPU max 81%** 로 두 자원이 동시에 한계에 근접. p95 는
19ms → 91ms (5배 ↑), p99 는 195ms cold spike → **sustained 200~300ms** 으로
질적 변화. 다만 어느 자원이 먼저 깨질지는 이 실험에서 단정 불가 — stage3 에서
분리 실험 필요.

## 한계 지표

| 항목 | 값 | 비고 |
|---|---|---|
| **한계 RPS** (p95 < 500ms 유지 기준) | ≥109 (한계 미도달) | 모든 threshold 여전히 통과 |
| p50 @ 한계 | 2.85ms (Grafana mean) / ~3.5ms (k6 med) | |
| p95 @ 한계 | **90.7ms (k6)** / 54.1ms mean (Grafana 1m window) | 목표 500ms 의 18% — 여유는 남음 |
| p99 @ 한계 | 155ms mean / **313ms max** (sustained 구간) | stage1 의 "cold spike 195ms" 와 질적으로 다름 |
| 실패율 (http_req_failed) | 0.00% (0 / 26813) | |
| iterations 총합 | 7737 | 31.5/s |
| k6 threshold | ✅ pass | `p(95)<500`, `p(99)<1500`, `rate<0.02`, `board_*_duration p(95)<400` 모두 통과 |

## 리소스 사용 관찰 (Grafana)

> Time range: 부하 시작 +30s 이후 (첫 램프 구간 제외). Legend mean/max/last 자동.

| 지표 | mean | max | last | 관찰 |
|---|---|---|---|---|
| `process_cpu_usage` | 40.6% | **81.0%** | 1.0% | stage1 의 50% 에서 급상승 — **포화 임박** |
| `jvm_memory_used{heap}` | — | 94.1 MiB | 74.4 MiB | 여유, 한도 512MB |
| `jvm_memory_used{nonheap}` | 145 MiB | 150 MiB | 145 MiB | 안정 |
| `jvm_gc_pause_seconds` rate | 1.05 ms/s | **3.71 ms/s** | 184 μs | stage1 2.31 → stage2 3.71 (60% 상승). 12:51:05 peak 후 감소 |
| `hikaricp_connections_active` | 1.11 | **9** | 0 | stage1 max 1 → 9. pool 거의 꽉 참 |
| `hikaricp_connections_idle` | 8.89 | 10 | 10 | |
| `hikaricp_connections_pending` | 0.09 | **5** | 0 | **첫 등장** — 커넥션 대기 발생, DB pool 한계 신호 |

## stage1 대비 변화

| 항목 | stage1 (VU 100) | stage2 (VU 200) | 변화 |
|---|---|---|---|
| RPS | 56 | **109** | ≈ 2배 ↑ (VU 선형 확장) |
| p95 (k6) | 19ms | **91ms** | 5배 ↑ |
| p99 max | 195ms (cold spike) | **313ms** (sustained) | 성격 변화: 일시 튐 → 지속적 고지연 |
| CPU max | 50% | **81%** | +31%p |
| HikariCP active max | 1 | **9** | DB 사용률 9배 ↑ |
| HikariCP pending max | **0** | **5** | **0 → 5 (병목 첫 신호)** |
| GC pause max | 2.31 ms/s | 3.71 ms/s | +60% |

**해석**: stage1 까지 "여유만 있던" 시스템이, VU 200 에서 **두 자원(CPU, DB pool)이 동시에 압박받는 상태** 로 전환. 하지만 완전 포화(CPU 100% / pool 지속 대기)는 아님 — 한계 직전의 "곡선이 꺾이기 시작하는 구간" 이다.

## 병목 가설

이번 실험에서 **처음으로 두 개의 유력 용의자** 가 보였다:

1. **HikariCP pool (size 10)** — active max **9**, pending max **5**.
   Pool 용량 대비 90% 활용하며 대기 큐 형성. 12:51:00~12:51:10 구간의 pending=5 는
   p99 peak 313ms 와 **시간상 정확히 겹침** → 인과 관계 강하게 시사.

2. **CPU (1.0 limit)** — max **81%**.
   포화는 아니지만 stage1 의 50% 대비 급상승. 부하의 80% 는 이미 쓰고 있음.

**단, 둘 다 "완전 포화" 는 아님**: pool idle 이 최소 1 이상 유지됐고 CPU 도
100% 미터치. 따라서 둘 중 무엇이 **먼저 깨질지 (p95>500ms 또는 실패율>0)** 는
이 실험만으론 단정할 수 없다. → stage3 에서 분리 실험 필요.

## 엔드포인트별 특이점

> `Latency p95 (s) by URI` 패널의 Legend 값.

| URI | p95 mean | p95 max | 관찰 |
|---|---|---|---|
| `GET /v1/articles` (list) | 89.9ms | **195ms** | 페이지네이션 쿼리, 가장 무거움 |
| `GET /v1/hot-articles/articles/date/{dateStr}` | 97.7ms | **204ms** | Redis 로 캐시되는 경로 **치고는 예상외로 느림** (아래 놀라운 점 참고) |
| `POST /v1/article-likes/.../pessimistic-lock-1` | 98.6ms | 182ms | write + 원자적 UPDATE |
| `GET /v1/articles/{articleId}` (read) | **13.8ms** | 88.8ms | Redis 캐시 효과, 여전히 가장 빠름 |
| `GET /v2/comments` | 23.7ms | 79.0ms | 정상 범위 |
| `/actuator/prometheus` | 89.7ms | 111ms | 메트릭 스크랩이 본 요청 수준 지연 (CPU 압박 부수 효과) |

## 놀라운 점 / 예상과 다른 점

1. **HikariCP active 가 stage1 max 1 → stage2 max 9.** DB 호출 빈도가 늘어난
   게 아니라 **동시 체류 시간** 이 늘어난 것. 쿼리 속도는 비슷한데 VU 200 이
   동시에 때리니 한 커넥션 안에 연속 재활용하던 stage1 패턴이 깨지고 커넥션이
   실제로 점유되는 시간이 쌓임. 이게 pool 의 "공급 한계" 를 드러낸 구조.

2. **`/v1/hot-articles` 가 list/likes 수준 느림 (mean 97.7ms, max 204ms).**
   인기글은 Redis 로 집계/캐시된다는 전제였는데 실측이 DB 경로와 비슷. 가능한
   원인: (a) Redis pipeline / 커넥션 contention, (b) cache miss 비율 증가,
   (c) Spring Data Redis + Jackson 직렬화 CPU 비용이 병목 CPU 에 눌림.
   다음 실험에서 `spring.data.redis.client-type=lettuce` 옵션이나 cache hit
   rate 로그 확인 필요.

## 다음 실험 계획

CPU와 DB 등 병목지점을 보다 정확하게 측정하기 위해 부하 시나리오 변경 결정.
효율적인 테스트를 위해 기존에는 가상 유저의 Sleep 시간을 1~2초 수준으로 압축하여 시나리오를 진행했지만,
실제 유저의 동작과 유사하게 Sleep 시간을 10~30초 수준으로 변경하고 VU도 800명으로 늘려 재테스트 예정

---

## 체크리스트

- [x] `env.md` 작성 (환경 고정)
- [x] `k6-summary.json` 저장 (`--summary-export`)
- [x] `k6-console.txt` 저장 (stdout)
- [x] `grafana-overview.png` 저장
- [x] `grafana-latency.png` 저장
- [x] `grafana-hikari.png` 저장
- [x] `grafana-gc.png` 저장
- [x] `../README.md` 요약 표에 한 줄 갱신
