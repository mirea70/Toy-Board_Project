# stage4 — 관찰 노트

> VU 800 → **1500** 상향. 모놀리틱 ×1 의 현실 시나리오 하 **진짜 한계** 탐색.
> stage3 과 같은 아키텍처, 같은 시나리오 방식, VU 만 1.87배 증가.

## 한 줄 요약

VU 1500 에서 **드디어 모놀리틱 ×1 의 병목이 지속적으로 드러났다. **HikariCP pending
max 16** (pool size 10 의 160% — 확실한 saturation), GC pause max 1.78 → 9.16
ms/s (5배 ↑), **처음으로 HTTP 실패 3건** 발생 (like path). 단 **CPU 는 여전히
max 70.8% 로 한계 미도달**

## 한계 지표 (k6 summary)

| 항목 | 값 | 비고 |
|---|---|---|
| **한계 RPS** (p95 < 500ms 유지 기준) | ≥160 (한계 미도달) | 모든 threshold ✓ pass |
| p50 | 2.91ms | |
| p95 (k6, 전체 run) | **89.16ms** | 목표 500ms 의 18% — 여유 남음 |
| p99 (Grafana, 전체 run) | mean **161ms** / max **341ms** | **지속형** 고지연 — stage3 의 "ramp transient" 와 성격 다름 |
| iterations 총합 | 43,830 (+959 interrupted) | 45.2 iter/s |
| iteration_duration 평균 | 27.1s | 설계값과 일치 |
| http_reqs | 154,789 | **159.59 req/s** |
| 실패율 | 0.00194% (3 / 154,789) | **처음으로 발생**. 3건 모두 like path (`✗ 3 / ✓ 2218`) |
| k6 threshold | ✅ pass | `p(95)<500`, `p(99)<1500`, `board_*_duration p(95)<400`, `rate<0.02` 전부 통과 |

## 리소스 사용 관찰 (Grafana)

> Time range: 16:09:40 ~ 16:25:00 UTC (첫 램프 1m 제외하고 잡음). Legend
> mean / max / last 자동.

| 지표 | mean | max | last | 관찰 |
|---|---|---|---|---|
| `process_cpu_usage` | **51.38%** | **70.80%** | 42.40% | stage3 max 62.8 → 70.8 (+8%p). **여전히 한계 미도달** (100% 미터치) |
| `jvm_memory_used{nonheap}` | 158 MiB | 163 MiB | 163 MiB | 안정 |
| `jvm_gc_pause_seconds` rate | 4.67 ms/s | **9.16 ms/s** | 4.27 ms/s | stage3 max 1.78 → **5배 ↑**. GC 가 본격적 압박 받음 |
| `hikaricp_connections_active` | 1.39 | **10** | 1 | pool size 10 — **max 10 도달 = 완전 포화** (stage3 max 5) |
| `hikaricp_connections_idle` | 8.61 | 10 | 9 | |
| `hikaricp_connections_pending` | 0.205 | **16** | 0 | pool size 10 의 **160% 대기 큐** 발생 — 16:21 경 명확한 spike |

### 엔드포인트별 p95 (mean only)

| URI | p95 mean | 관찰 |
|---|---|---|
| `GET /v1/articles/{articleId}` | 9.93ms | Redis 캐시 경로, 여전히 가장 빠름 |
| `GET /v2/comments` | 27.4ms | 안정 |
| `GET /v1/articles` (list) | 78.9ms | 페이지네이션 쿼리, 주요 DB 부하 |
| `POST /v1/article-likes/.../pessimistic-lock-1` | **118ms** | 쓰기 경로, pool saturation 영향 |
| `GET /v1/hot-articles/.../dateStr` | **130ms** | **stage3 (30.7ms) → 130ms, 4배 악화** — stage2 와 동일 패턴 재등장 |
| `/actuator/prometheus` | 132ms | 메트릭 스크랩이 130ms — CPU/GC 부하의 부수 효과 |

## stage3 대비 변화 (VU 800 → 1500, 같은 시나리오)

| 항목 | stage3 (VU 800) | stage4 (VU 1500) | 변화 |
|---|---|---|---|
| RPS | 88 | **160** | +82% (VU 배수 1.87 과 거의 일치, 선형 확장) |
| p95 (k6) | 19ms | **89ms** | 4.7배 ↑ |
| p99 max (Grafana) | 850ms (ramp transient) | 341ms (지속 고지연) | 성격 반전: stage3 는 일시 쇼크, stage4 는 **sustained** |
| CPU max | 62.8% | **70.8%** | +8%p (여전히 여유 있음) |
| Hikari active max | 5 | **10** | **pool 완전 포화 재등장** |
| Hikari pending max | 0 | **16** | **pool 160% 대기 — 병목 명확** |
| GC pause max | 1.78 ms/s | **9.16 ms/s** | **5배 ↑** |
| 실패 건수 | 0 | **3 (like path)** | 처음으로 발생 |

## 병목 가설

**이번엔 확실하다 — 모놀리틱 ×1 의 병목은 두 곳.**

### 1. HikariCP pool (size 10) — 주 병목

- active **10 도달** (완전 포화), pending peak **16** (pool 1.6배의 대기 큐)
- 16:21 경의 pending spike 가 **latency p99 peak (300ms+) 와 시간상 정확히 겹침**
- stage2 (VU 200 압축) 에서도 pending 5 가 병목 신호였는데, stage4 에서 훨씬 강화된 형태로 재등장
- **pool size 10 이 이 시스템의 실질 천장**

### 2. JVM GC — 보조 병목

- GC pause rate max 9.16 ms/s = 초당 약 9ms 가 GC 에 소모 (1%)
- mean 4.67 ms/s 이 지속됨 = 평균적으로 5% 가까운 GC overhead
- 할당률 급증: VU 1500 의 동시 세션 수 증가로 객체 생성 속도 가속
- CPU 여유는 있지만 GC 가 늘어난 만큼 **실질 처리 CPU** 는 줄어들고 있음

### 3. CPU 는 병목이 **아님** (중요한 확정)

- max 70.8%, 100% 미터치
- stage2 에서 "CPU 81%" 가 의심받았지만, 그건 burst 패턴의 단일 peak 였고
  stage3~4 로 가면서 sustained load 기준으로는 CPU 에 명확한 여유가 있음
- **VU 를 더 올려도 CPU 가 먼저 깨지진 않을 것** — DB/GC 가 먼저

## 예상과 다른 점

### 1. CPU 가 포화되지 않는다

- **관찰**: CPU max 가 VU 200 / 800 / 1500 에서 각각 **81% / 63% / 71%**. 단조 증가 안 함.
- **원인**: burst (stage2) 는 순간 peak 높음, 분산 (stage3~4) 은 peak 낮지만 지속적 중간 부하.
- **교훈**: "CPU max" 단일 숫자로 시스템 건강도 판단은 위험. **peak + sustained + 패턴** 을 같이 봐야 함.

### 2. HikariCP pending 16 = pool 크기 (10) 의 1.6배

- **관찰**: active **10** + pending **16** = 순간 **26개의 DB 쿼리 요구** 동시 발생.
- **맥락**: VU 1500 중 대부분이 sleep 중인 상황에서도 26 동시 쿼리는 무거움 → burst 순간의 집중.
- **다음 단계**: `hot-articles` 가 Redis 캐시를 **우회하는 경로** 가 있는지 코드 확인 필요.

### 3. hot-articles 가 또 느려짐 (130ms)

- **관찰**: stage3 30.7ms → stage4 **130ms** (4배 악화). stage2 의 97ms 보다도 느림.
- **추측**: Redis 연결 pool 또는 Spring Data Redis contention 이 VU 증가에 **비선형으로** 악화.
- **다음 단계**: stage4 의 주 의심점으로 등록. Redis 접근 패턴 / cache miss 로그 확인.

### 4. 처음으로 HTTP 실패 발생 (like 3건)

- **관찰**: 3 / 154,789 = **0.00194%**.
- **의미**: 분석 결과 시나리오 상, MAX_USER_ID가 1000이어서 동일한 유저가 좋아요를 여러번 수행 가능한 상태였음. 또한 코드 상으로도 방어 코드 추가 필요 확인 
- **실무 함의**: threshold 통과해도 "첫 실패" 는 중요한 신호.

### 5. GC pause 가 stage3 → stage4 에서 5배 폭증

- **관찰**: GC pause rate max 가 stage2 3.71 → stage3 1.78 → stage4 **9.16 ms/s**.
- **패턴**: stage2~3 에서 "오히려 감소" 였는데 stage4 에서 급증
- **시사점**: VU 800 과 1500 사이 어딘가에 **임계점** 존재. stage4-branch 분기 실험에서 좁혀볼 가치 있음.

## 다음 실험 계획

**HikariCP pool 10 → 30 올린 채 VU 1500 재측정 (stage4-branch)**

---

## 체크리스트

- [x] `env.md` 작성 (환경 고정)
- [x] `k6-summary.json` 저장 (✓ 이번엔 성공)
- [x] `k6-console.txt` 저장
- [x] `grafana-overview.png` 저장
- [x] `grafana-latency.png` 저장
- [x] `grafana-hikari.png` 저장
- [x] `grafana-gc.png` 저장
- [x] `../README.md` 요약 표에 한 줄 갱신
