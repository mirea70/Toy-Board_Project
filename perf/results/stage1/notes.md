# stage1 — 관찰 노트

> CPU 0.5 → 1.0 상향, VU 50 → 100 상향.
> 모놀리틱 단일 인스턴스의 실제 한계를 탐색하는 실험.

## 한 줄 요약

CPU 를 2배로 올리고 VU 도 2배로 올렸는데 **어떤 자원도 포화되지 않았다.**
RPS 는 29 → 56 으로 거의 선형 확장, p95 는 99ms → 19ms 로 오히려 5배 개선,
CPU max 50% / HikariCP active max 1 / GC pause 감소 추세. VU 100 은
모놀리틱 단일 인스턴스의 한계에 한참 못 미치는 수준 → 다음 실험에서 VU 200 증대 예정.

## 한계 지표

| 항목 | 값 | 비고 |
|---|---|---|
| **한계 RPS** (p95 < 500ms 유지 기준) | ≥56 (한계 미도달) | 모든 threshold 통과 |
| p50 @ 한계 | 2.67ms (mean) / 6.54ms (max) | |
| p95 @ 한계 | 19.2ms (mean) / 60.7ms (max) | 목표 500ms 의 3.7% |
| p99 @ 한계 | 46.1ms (mean) / 195ms (max) | max 는 초반 cold 스파이크 (아래 참고) |
| 실패율 (http_req_failed) | 0.00% (0 / 13780) | |
| iterations 총합 | 3966 | 16.2/s |
| k6 threshold | ✅ pass | `p(95)<500`, `p(99)<1500`, `rate<0.02` 모두 통과 |

## 리소스 사용 관찰 (Grafana)

> Legend `table` 모드로 mean/max/last 자동 수집됨.

| 지표 | mean | max | last | 관찰 |
|---|---|---|---|---|
| `process_cpu_usage` | 28% | **50%** | 1.8% | **한도(1.0) 대비 절반만 사용** — CPU 병목 아님 |
| `jvm_memory_used{heap}` | 79.7 MiB | 118 MiB | 82.5 MiB | 512MB 한도 대비 충분한 여유 |
| `jvm_memory_used{nonheap}` | 147 MiB | 157 MiB | 142 MiB | metaspace 등 안정적 |
| `jvm_gc_pause_seconds` rate | 1.11 ms/s | 2.31 ms/s | 145 μs | **감소 추세** (stage0 단조증가의 정반대) |
| `hikaricp_connections_active` | 0.109 | **1** | 0 | pool size 10 중 실질 1개 — DB **거의 유휴** |
| `hikaricp_connections_pending` | 0 | **0** | 0 | 커넥션 대기 전혀 없음 |

## stage0 대비 변화

| 항목 | stage0 (CPU 0.5, VU 50) | stage1 (CPU 1.0, VU 100) | 변화 |
|---|---|---|---|
| RPS | ~29 | **56.3** | ≈ 2배 ↑ (CPU·VU 선형 확장) |
| CPU | 65% | **50%** | ↓ (제한 상향으로 여유 확보) |
| p95 | 99ms | **19ms** | **≈ 5배 ↓** |
| p99 | ~760ms | 46ms (max 195ms spike) | 대폭 ↓ |
| GC pause 추세 | 단조증가 | **감소** | **역전** — CPU 여유가 GC에 돌아감 |
| HikariCP pending | 0 (VU 50) | 0 | 동일 — DB 여유 |

**해석**: stage0 에서 관찰했던 "CPU 65% + GC 단조증가" 는 **CPU 제한이 GC 실행 자체를 압박하던 상태**였을 가능성이 높다. 1.0 으로 올리자 GC 가 heap 을 계속 비울 수 있어 pause rate 가 오히려 감소했다.

## 병목 가설

**이 부하 수준에서는 병목이 없음.** 근거:
- CPU: 한도의 50% 만 씀
- DB: HikariCP active max 1 (pool size 10), pending 계속 0
- 메모리: heap 118MB / limit 512MB
- GC: 시간이 지날수록 개선되는 방향

따라서 "모놀리틱의 한계 RPS" 는 이 실험에서 관찰되지 않았다. VU 를 더 올려야 한계 지점이 드러난다.

## 엔드포인트별 특이점

> `Latency p95 (s) by URI` 패널의 Legend 값.

| URI | p95 mean | p95 max | 관찰 |
|---|---|---|---|
| `GET /v1/articles` (list) | 34.9ms | **194ms** | 전체 p99 max(195ms) 와 거의 일치 — **초반 스파이크 주범** |
| `GET /v1/articles/{articleId}` (read) | 6.21ms | 24.4ms | 조회 안정, 캐시 효과로 보임 |
| `GET /v1/hot-articles/articles/date/{dateStr}` | 12.1ms | 54.0ms | 정상 |
| `GET /v2/comments` | 9.95ms | 16.5ms | 정상 |
| `POST /v1/article-likes/.../pessimistic-lock-2` | 32.7ms | 85.1ms | 비관적락 쓰기, 예상 범위 내 |
| `/actuator/prometheus` | 28.7ms | 38.1ms | 메트릭 스크랩 — 기능 정상 |

## 놀라운 점 / 예상과 다른 점

1. **CPU 를 올렸더니 CPU 사용률이 오히려 떨어진다 (65% → 50%).**
   단순 증량이 아니라 GC throttle 이 풀리면서 처리 효율이 올라간 결과로 해석.
   즉 stage0 의 CPU 65% 는 "CPU 를 65% 썼다" 가 아니라 **"할당 한도까지 GC 에
   눌려 있었다"** 는 해석이 더 정확할 수 있다.

2. **초반 `GET /v1/articles` p95 가 194ms 까지 튐** — smoke warmup 을 돌렸음에도.
   가설: smoke 는 별도 k6 컨테이너에서 실행 → 본 부하 실행 시 **k6 HTTP
   클라이언트 커넥션이 다시 cold**. 서버 JIT 은 웜이지만 클라이언트측
   connection establishment 비용이 초반 15초에 몰린다.
   대응 후보: (a) 측정 구간에서 초반 15~30초 제외, (b) k6 `--no-vu-connection-reuse=false` 유지 + 워밍업 phase 내장.

3. **HikariCP active max = 1.** VU 100 이 동시에 요청해도 DB 쿼리가 충분히
   빨라서 한 커넥션을 연속 재활용하는 수준. Redis 캐시 / 쿼리 단순성 효과.

## 다음 실험 계획

- **stage2**: 동일 조건(CPU 1.0, 모놀리틱 ×1) 에서 **VU 100 → 200 으로 상향**,
  실제 한계 탐색. 시나리오 stages 를 `30s→50 VU / 1m→200 VU / 2m hold / 30s→0`
  으로 조정 예정.
- 관찰 목표: CPU 가 먼저 100% 찍히는지, 아니면 DB pool 이 먼저 포화(pending > 0)
  되는지. 이 답이 **"모놀리틱의 진짜 한계 병목은 무엇인가"** 를 결정한다.
- 보조: 초반 스파이크 제거를 위해 측정 time range 를 부하 시작 +30s 부터로
  잡는 방식도 시도.

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
