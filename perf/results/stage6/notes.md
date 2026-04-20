# stage6 — 관찰 노트

> VU 1500 → **3000** 상향. pool=30 유지. 모놀리틱 ×1 의 **진짜 RPS 천장**
> 탐색. 환경 완전 동일, VU 만 변경.

## 결과 요약

- 모놀리틱 ×1 의 한계 도달.
- CPU max **100%** 찍힘
- HikariCP **pool 30 완전 포화 + pending 25** 대기 큐 발생
- GC pause max **17ms/s** (stage5 의 4배).
- 단일 노드의 실질 운용 한계로 파악.

## 한계 지표 (k6 summary)

| 항목 | 값 | 비고 |
|---|---|---|
| **한계 RPS** (p95 < 500ms 기준) | **~306 (한계 도달, 일부 threshold 실패)** | 처음으로 임계선 넘음 |
| p50 (http_req med) | 8.59ms | 여전히 빠름 — 대부분 요청은 정상 처리 |
| p95 (k6, 전체 run) | **507.56ms** ❌ | threshold p(95)<500 미세하게 실패 |
| p99 (Grafana) | mean **627ms** / max **1.83s** | |
| board_list_duration p95 | **986ms** ❌ | threshold p(95)<400 대폭 실패 (2.5배) |
| board_read_duration p95 | 385ms ✓ | 아슬하게 통과 |
| iterations 총합 | 89,310 (+1918 interrupted) | 86.7 iter/s |
| iteration_duration 평균 | 27.4s | 목표 설계값과 일치 |
| http_reqs | 315,509 | **306.34 req/s** |
| 실패율 | 0.001% (3 / 315,509) | read 2건, comment 1건 — 무시할 수준 |
| k6 threshold | ❌ 2개 실패 | `board_list<400`, `http_req<500` / 나머지 ✓ |

## 리소스 사용 관찰 (Grafana)

> Time range: 20:08:00 ~ 20:25:00 UTC. Legend mean/max/last 자동.
> stage5 (VU 1500) → stage6 (VU 3000) 인라인 비교 포함.

| 지표 | stage5 max | stage6 max | stage6 mean | 배율 |
|---|---|---|---|---|
| `process_cpu_usage` | 70.8% | **100%** | 74.05% | saturate 도달 |
| `jvm_gc_pause_seconds` rate | 4.09 ms/s | **17.0 ms/s** | 6.17 ms/s | ×4.2 |
| `hikaricp_connections_active` | 8 (of 30) | **30** (of 30) | 6.44 | pool 완전 포화 |
| `hikaricp_connections_pending` | 0 | **25** | 0.200 | 대기 큐 등장 |
| **RPS** | 160 | **306** | — | +91% (선형 확장) |

### 엔드포인트별 p95 (mean, Grafana) — 전 경로 일제히 악화

| URI | stage5 | stage6 | 배율 |
|---|---|---|---|
| `GET /v1/articles/{articleId}` | 7.98ms | **130ms** | **×16** (Redis 캐시인데도 악화) |
| `GET /v2/comments` | 15.7ms | 89.2ms | ×5.7 |
| `GET /v1/articles` (list) | 46.0ms | **506ms** | ×11 (threshold 위반 주범) |
| `GET /v1/hot-articles/.../dateStr` | 63.4ms | 301ms | ×4.8 |
| `POST /v1/article-likes/.../pessimistic-lock-1` | 82.4ms | **439ms** | ×5.3 |
| `/actuator/prometheus` | 76.9ms | 235ms | ×3 |

## 분석

### CPU 포화가 latency 를 전방위 전염시킨다

`GET /v1/articles/{id}` 는 Redis 캐시 경로인데도 p95 가 ×16 악화. Redis 자체가 느려진 게 아니라 **CPU 100% 에서 스레드 dispatch 대기 시간이 response time 에 그대로 포함**되는 것. `/actuator/prometheus` 235ms 도 같은 이유. 즉 CPU 포화 구간에선 **"빠른 경로" 와 "느린 경로" 구분이 무의미** — 모두가 CPU 대기에 갇힘.

### RPS 가 예상보다 높음 (예상 225, 실측 306)

stage5 선형 외삽은 "VU 2100 에서 CPU 100%, RPS ~225" 였으나 실측은 VU 3000 에서 RPS 306. 원인: CPU 70→100% 구간에서 **queue 누적으로 throughput 은 계속 오르지만 latency 급증**. **RPS 최대치(306) ≠ 실운용 한계**. p95 < 500ms 유지 가능한 실운용 선은 **VU 2100~2500 (RPS 225~280)** 구간으로 해석해야 함.

## 관찰 정리

- CPU에 과부하가 걸려, 전체적으로 성능이 떨어진 것을 체감할 수 있었음. 현재 스펙 기준 단일 노드의 한계로 파악됨.
- **실패율은 여전히 0.001%** (3건) — CPU 100% 포화인데도 HTTP 500 은 거의 없음. Tomcat이 queue를 이용해 유실을 최소화한 것으로 파악됨. 

## 다음 실험 계획

**stage7: 모노 ×2 + Nginx (스케일 아웃)**

- stage6 의 baseline (모노 ×1 포화 RPS 306) 을 기준으로 **app 레플리카 ×2** 효과 측정
- VU 3000 유지
- HikariCP pool 30으로 유지
- **공유 MySQL 이 새로운 병목** 이 될 지 파악 필요

---

## 체크리스트

- [x] `env.md` 작성 (환경 고정)
- [x] `k6-summary.json` 저장
- [x] `k6-console.txt` 저장
- [x] `grafana-overview.png` 저장
- [x] `grafana-latency.png` 저장
- [x] `grafana-hikari.png` 저장
- [x] `grafana-gc.png` 저장
- [x] `../README.md` 요약 표에 한 줄 갱신
