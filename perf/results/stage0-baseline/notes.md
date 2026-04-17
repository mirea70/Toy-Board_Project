# stage0-baseline — 관찰 노트

> 부하 완료 후 이 파일을 채웁니다.
> 템플릿 원본: [`../TEMPLATE.md`](../TEMPLATE.md)

## 한 줄 요약

모놀리틱 1 replica (0.5 CPU) 기준, 50 VU / ~29 RPS 에서 p95 = 99ms 로 임계치(500ms) 내 통과했으나,
CPU 64.71% 소모, p99 피크 ~760ms, GC pause 단조 증가(100µs → 2.8ms) — **CPU와 GC가 한계에 접근 중이며, VU를 더 올리면 곧 꺾일 것으로 예상.**

## 한계 지표

| 항목 | 값 | 비고 |
|---|---|---|
| **한계 RPS** (p95 < 500ms 유지 기준) | ≥ 29 (한계 근접) | p99가 760ms까지 튀어 p(99)<1500 여유 50% |
| p50 @ 한계 | 6.47 ms | Grafana 정상 구간 ~4ms |
| p95 @ 한계 | 99.17 ms | Grafana 피크 ~220ms (ramp-up), 정상 구간 ~100ms |
| p99 @ 한계 | ~760 ms (Grafana 피크) | ramp-up 시 760ms, hold 구간 300~400ms, 후반 ~200ms |
| 실패율 (http_req_failed) | 0.00% (0 / 7,000) | |
| iterations 총합 | 2,006 | |
| k6 threshold | ✅ pass (전체 통과) | `p(95)<500`, `p(99)<1500`, `rate<0.02` 모두 통과 |

## 리소스 사용 관찰 (Grafana)

| 지표 | 피크 시점 | 피크 값 | 관찰 |
|---|---|---|---|
| `process_cpu_usage` | 실험 구간 전체 | **64.71%** | 0.5 core 한도의 ~65%. 여유가 많지 않음. **CPU가 1차 병목 후보** |
| `jvm_memory_used_bytes{area=heap}` | 23:25~23:26 | ~160 MB | 512MB 한도 대비 약 31%. heap 자체는 여유 |
| `jvm_gc_pause_seconds` 누적 | 23:27:50 | **2.8 ms** | 100µs → 2.8ms로 **단조 증가**. 부하가 지속될수록 GC 압력 상승. 안정화 없이 계속 올라감 |
| `hikaricp_connections_active` | 23:26:20, 23:27:30 | 4 | 최대 풀(10) 대비 40%. 간헐적 스파이크 |
| `hikaricp_connections_pending` | — | 0 | **전 구간 0 — DB 커넥션 대기 없음** |

## 병목 가설

**CPU가 1차 병목, GC가 2차 병목이다.**

- **CPU 64.71%**: 0.5 core 제한 하에서 이미 65% 소모. VU를 더 올리면 CPU 포화로 레이턴시가 급격히 증가할 것.
- **GC pause 단조 증가**: 100µs에서 시작해 실험 종료 시점 2.8ms까지 안정화 없이 상승. 50 VU hold 구간(2분) 동안에도 계속 올라감. 이는 allocation rate가 GC 처리 능력에 근접하고 있음을 시사. CPU 제한이 GC 스레드에도 영향을 주어 GC 효율이 떨어지는 악순환 가능성.
- **p99 피크 760ms**: ramp-up 시점(23:25:05)에 VU가 급증하면서 발생. 이는 JIT 컴파일 + CPU 경쟁이 겹친 결과. hold 구간에서도 300~400ms 유지되어 p(99)<1500 임계치의 50% 수준.
- **HikariCP**: pending 0, active 최대 4/10 — DB 풀은 아직 여유. 병목이 아님.

## 엔드포인트별 특이점

| URI | p95 | 관찰 |
|---|---|---|
| `GET /v1/articles` | 199.57 ms | 목록 조회가 가장 느림 — 페이지네이션 쿼리 부하. board_list_duration p95 |
| `GET /v1/articles/{id}` | 78.25 ms | 단건 조회는 상대적으로 가벼움. board_read_duration p95 |
| `GET /v1/hot-articles/...` | — | 35% 확률로만 호출 (718회). overview 상 p95 안정적 |
| `GET /v2/comments` | — | 목록 조회 후 바로 호출. overview 상 낮은 레이턴시 |
| `POST /v1/article-likes` | — | 107회 호출. overview Latency p95 by URI에서 pessimistic-lock 경로 **p95 ~800ms까지 스파이크** 관찰 |
| `POST /v2/comments` | — | 39회. 샘플 적어 통계 의미 제한적 |
| `POST /v1/articles` | — | 11회. 샘플 적어 통계 의미 제한적 |

## 놀라운 점 / 예상과 다른 점

- **CPU 사용률이 예상보다 높음**: 0.5 core에 29 RPS인데 65% 소모. 단순 CRUD 게시판치고 CPU 효율이 낮은 편. JIT 최적화, 직렬화/역직렬화, 혹은 ORM 오버헤드를 의심할 수 있음.
- **GC pause가 안정화되지 않음**: 보통 JIT 워밍업 후 GC가 안정화되는데, 이 실험에서는 4분 내내 단조 증가. 이는 부하가 지속되면 GC로 인한 stop-the-world가 레이턴시에 직접 영향을 줄 수 있음을 의미.
- **p99가 p95와 큰 격차**: p95=99ms vs p99 피크=760ms (약 7.7배). 꼬리 지연이 심한 분포. 소수 요청이 GC pause나 CPU 경쟁에 의해 크게 지연됨.
- **좋아요(pessimistic-lock) 경로 p95 ~800ms**: overview의 URI별 p95 차트에서 가장 높은 지연. 비관적 락이 CPU 경쟁 상황에서 대기 시간을 증폭시키는 것으로 보임.
- **목록 조회가 단건 조회보다 p95 기준 2.5배 느림** (200ms vs 78ms): 페이지네이션 쿼리가 상대적 병목 후보.

## 다음 실험 계획

- 다음 단계: `stage1-scale-2` — `docker compose up -d --scale app=2` 후 동일 부하 재측정
  - CPU가 1차 병목이므로, 레플리카 추가로 CPU 분산 효과를 직접 확인
- **분기 실험 후보**: VU를 100~200으로 올려 실제 한계(p95>500ms 되는 지점)를 파악
- 목록 조회(`/v1/articles`) 쿼리 최적화 → stage2(CQRS)에서 읽기 분리 효과 기대
- 좋아요 pessimistic-lock p95 ~800ms → stage3(좋아요 서비스 독립)에서 개선 여부 확인
- GC 단조 증가 원인 조사: heap 덤프 또는 allocation profiling으로 주 할당 원인 파악 고려

---

## 체크리스트

- [x] `env.md` 작성 (환경 고정)
- [x] `k6-summary.json` 저장 (`--summary-export`)
- [x] `k6-console.txt` 저장 (stdout)
- [x] `grafana-overview.png` 저장
- [x] `grafana-latency.png` 저장
- [x] `grafana-hikari.png` 저장
- [x] `grafana-gc.png` 저장
- [x] `../README.md` 요약 표에 `stage0-baseline` 한 줄 갱신
