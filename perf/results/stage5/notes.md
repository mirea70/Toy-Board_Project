# stage5 — 관찰 노트

> **분기 실험**: stage4 (VU 1500, pool 10) 의 **pool saturation (pending 16)**
> 이 실제 주 병목이었는지 격리 검증. stage4 와 완전히 동일한 환경에서
> **HikariCP maximum-pool-size 만 10 → 30** 으로 변경.

## 한 줄 요약

**Hikari Pool 이 stage4 latency 의 주 원인 중 하나였음이 확정됐다.** Pool 을 3배로
올리자 **p95 89ms → 29ms (3배 개선)**, Hikari pending **16 → 0** 완전 해소,
GC pause max 도 9.16 → 4.09 ms/s 로 절반 이하.
— pool 은 latency 를 해결했지만 **RPS 의 한계는 CPU (여전히 70.8%)** 에
걸린 것으로 확인됨. 또한 최근 코드 개선 (409 변환 + MAX_USER_ID 30000) 으로
**HTTP 실패 3건 → 0건** 완전 해소.

## 한계 지표 (k6 summary)

| 항목 | 값 | 비고 |
|---|---|---|
| **한계 RPS** (p95 < 500ms 유지 기준) | ≥160 (한계 미도달) | 모든 threshold ✓ pass |
| p50 | 2.82ms (http_req med) | |
| p95 (k6, 전체 run) | **29.3ms** | stage4 89ms → 29ms (3배 개선) |
| p99 (Grafana) | mean **116ms** / max **503ms** | max 는 19:16 경 단일 spike (아래 참고) |
| iterations 총합 | 43,930 (+970 interrupted) | 45.3 iter/s |
| iteration_duration 평균 | 27.03s | 설계값 일치 |
| http_reqs | 155,095 | **159.9 req/s** |
| 실패율 | **0.00% (0 / 155,095)** | stage4 의 3건 실패 완전 해소 |
| k6 threshold | ✅ pass | 모두 통과 |

## 리소스 사용 관찰 (Grafana)

> Time range: 19:05:00 ~ 19:21:00 UTC (첫 램프 일부만 포함). Legend mean/max/last 자동.

| 지표 | mean | max | last | 관찰 |
|---|---|---|---|---|
| `process_cpu_usage` | 42.99% | **70.80%** | 0.95% | stage4 max 70.8% 와 **완전 동일** |
| `jvm_gc_pause_seconds` rate | 2.14 ms/s | **4.09 ms/s** | 345 μs | stage4 max 9.16 → **절반 이하**. pool 대기 제거 → GC 부담 경감 |
| `hikaricp_connections_active` | 0.653 | **8** | 0 | pool 30 중 **최대 8개만** 활용 — pool 30 은 **과잉 용량** |
| `hikaricp_connections_idle` | 29.3 | 30 | 30 | |
| `hikaricp_connections_pending` | **0** | **0** | **0** | stage4 max 16 → 완전 해소 |

### 엔드포인트별 p95 mean

| URI | stage4 | stage5 | 변화 |
|---|---|---|---|
| `GET /v1/articles/{articleId}` | 9.93ms | **7.98ms** | -20% (Redis 캐시 경로, 원래 pool 영향 적음) |
| `GET /v2/comments` | 27.4ms | **15.7ms** | -43% |
| `GET /v1/articles` (list) | 78.9ms | **46.0ms** | **-42%** |
| `GET /v1/hot-articles/.../dateStr` | 130ms | **63.4ms** | **-51%** (stage4 의 의심점, pool 효과로 절반) |
| `POST /v1/article-likes/.../pessimistic-lock-1` | 118ms | **82.4ms** | -30% |
| `/actuator/prometheus` | 132ms | 76.9ms | -42% (부수 효과) |

## stage4 대비 변화 (VU 1500 동일, pool 10 → 30)

| 항목 | stage4 | stage5 | 변화 |
|---|---|---|---|
| **RPS** | 160 | 160 | **동일** — pool 은 RPS 천장이 아님 |
| **p95 (k6)** | 89ms | **29ms** | **3배 개선** |
| **p99 max (Grafana)** | 341ms (sustained) | **503ms** (single spike) | 성격 반전: sustained → 대부분 구간 ~100ms + 한 번 spike |
| **p99 mean** | 161ms | 116ms | -28% |
| **CPU max** | 70.8% | **70.8%** | **완전 동일** — CPU 천장이 공통 제약 |
| **CPU mean** | 51.4% | 43.0% | -16% (pool 대기 시간 감소 → CPU 실효율 ↑) |
| **Hikari pending max** | **16** | **0** | **완전 해소** |
| **Hikari active max** | 10 (pool 10 = 100%) | **8** (pool 30 의 27%) | pool 30 은 **과잉 용량** |
| **GC pause max** | 9.16 ms/s | 4.09 ms/s | **절반 이하** |
| **실패 건수** | 3 | **0** | 완전 해소 (409 변환 + MAX_USER_ID 30000) |

## 병목 가설 검증

**stage4 에서 제시했던 가설**: 주 병목은 HikariCP pool (pending 16) + GC

**stage5 로 확인된 것**:

### ✅ 확정: pool 이 **latency** 의 주 원인이었다
- Pool 30으로 올리자 pending 0, p95 3배 개선, 대부분 엔드포인트가 40~50% 빨라짐
- stage4 에서 "pool pending 과 p99 peak 가 시간상 겹침" 이 인과 관계로 확인됨

### ✅ 확정: pool 은 **RPS** 천장이 아니었다
- pool 30으로 올려도 RPS 는 160 그대로 — pool 을 더 늘려도 RPS 증가 안 함
- **RPS 천장 = CPU** (둘 다 max 70.8%). app 컨테이너 cpu=1.0 제한이 RPS 의 hard ceiling.

### ✅ 부수 발견: pool 개선 → GC 개선
- pool 대기 시간이 줄면 스레드가 더 빨리 응답 완료 → 객체 수명 짧아짐 → GC 부담 감소
- stage4 에서 "GC 가 5배 증가" 의 원인 중 일부는 pool saturation 에서 온 **간접 효과**
- stage5 에서 GC max 9.16 → 4.09 로 절반 이하

### 🤔 새로 관찰: 19:16 경 **p99 503ms single spike**
- 그 외 구간은 p99 100~200ms 안정 상태 유지
- Hikari pending 은 여전히 0, pool 여유 있음 (active max 8)
- **GC pause 도 같은 시점에 peak (4.09ms)** — 지연의 공범
- 가설: minor GC 가 이 구간에 연속으로 몰렸거나, MySQL 쪽에서 일시 slow query (예: background 작업) 발생
- 추후 관찰할 만하지만 stage5 가 성공했으니 심층 분석은 후속 실험에서

## 회고

1. **pool size 는 "기본값 10" 이 너무 작음**. 모놀리틱 단일
   인스턴스에 VU 1500 수준 부하가 걸리면 pool 10 이 금세 차고 큐가 쌓임.
   


2. **pool 30 은 과잉**. active max 8 밖에 안 썼으니, 실제로는 15~20 정도가
   적정선으로 보임. 다만 튜닝의 목적이 "병목 제거" 라 과잉이라도 문제없음.
   프로덕션이면 `maximum-pool-size = 20` 정도로 재조정하는 게 합리적.

## 다음 실험 계획

**stage6: VU 3000명 수준으로 재테스트**

- 예상 관찰:
  - CPU: CPU 부하에 따른 병목지점 다수 생성될 것으로 예상됨
- 소요 16분

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
