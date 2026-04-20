# stage7 — 관찰 노트

> **Scale-out 실험**. stage6 (모노 ×1, CPU 100% 포화) 에 대한 수평 확장 대응.
> 동일 부하(VU 3000) + 동일 pool(30) 환경에서 **레플리카 1 → 2** 로만 변경.

## 결과 요약

- **scale-out 이 latency 를 완전 복구**: p95 507ms → **16.5ms** (×31 개선), threshold 전부 통과
- 공유 MySQL 이 병목 될까 우려했으나, 각 레플리카 Hikari active max 3~4 (pool 30 중) 로 **완벽히 여유** — VU 3000 수준에선 MySQL 아직 여유
- **CPU 도 양쪽 인스턴스 모두 ~36% mean, max 55~62%** — 레플리카 ×2 가 VU 3000 에 대해 **오버 엔지니어링 수준의 여유**

## 한계 지표 (k6 summary)

| 항목 | 값 | 비고 |
|---|---|---|
| **한계 RPS** (p95 < 500ms 기준) | ≥310 (한계 미도달, 상한 미탐색) | threshold 모두 ✓ pass |
| p50 (http_req med) | 3.24ms | stage6 8.59 대비 -62% |
| p95 (k6, 전체 run) | **16.49ms** ✓ | stage6 507 대비 **×31 개선** |
| p99 (Grafana) | mean **39.1ms** / max **386ms** | max 는 램프 2 시점 (초반 transient) |
| board_list_duration p95 | 14.93ms ✓ | stage6 986 → 14.93 (**×66 개선**) |
| board_read_duration p95 | 5.44ms ✓ | stage6 385 → 5.44 (×71 개선) |
| iterations 총합 | 90,579 (+1920 interrupted) | 87.9 iter/s |
| http_reqs | 319,720 | **310.4 req/s** |
| 실패율 | **0.00% (0 / 319,720)** | stage6 의 3건 실패도 해소 |
| k6 threshold | ✅ **전부 pass** | stage6 의 2개 실패 → stage7 에서 모두 통과 |

## 리소스 사용 관찰 (Grafana)

> Time range: 23:33:00 ~ 23:51:00. 레플리카당 자원은 인스턴스별 series 로 표시.
> stage6 (모노 ×1) ↔ stage7 (모노 ×2) 비교 포함.

| 지표 | stage6 max | stage7 max (인스턴스별) | stage7 mean | 관찰 |
|---|---|---|---|---|
| `process_cpu_usage` | **100%** (saturate) | **62.2% / 55.0%** | 35.97% / 37.15% | **포화 완전 해소**. 양쪽 인스턴스 거의 균등 (Nginx 라운드로빈 정상 동작) |
| `jvm_gc_pause_seconds` rate (합) | **17.0 ms/s** | 7.51 ms/s | 4.13 ms/s | **×2.3 감소** (각 인스턴스 ~3.75 ms/s max). 할당률 분산 효과 |
| `hikaricp_connections_active` (per 인스턴스) | **30** (of 30) | **3 / 4** (of 30 each) | 0.42 / 0.34 | pool **거의 놀고 있음**. 공유 DB 경합 미관찰 |
| `hikaricp_connections_pending` (per 인스턴스) | **25** | **0 / 0** | 0 / 0 | **완전 해소** |
| **RPS** | 306 | — | **310** | 거의 동일 — 부하 자체가 상한 |

### 엔드포인트별 p95 — 전 경로 드라마틱 개선

| URI | stage6 (mono) | stage7 (×2) | 배율 |
|---|---|---|---|
| `GET /v1/articles/{articleId}` | 130ms | ~5ms | **×26 개선** |
| `GET /v2/comments` | 89.2ms | ~15ms | ×6 |
| `GET /v1/articles` (list) | 506ms | ~20ms | **×25 개선** |
| `POST /v1/article-likes/.../pessimistic-lock-1` | 439ms | ~30ms | ×15 |

> stage7 per-URI 값은 Grafana 패널이 "mean 값만 표시" 로 잘림. k6 기준 전체 http_req_duration p95 = 16.49ms 로 일관 개선 확인.

## 분석

### 공유 DB 상 병목은 관찰안됨

stage6 에서 "scale-out 하면 공유 MySQL 이 새 병목될 것" 을 우려했으나 실측:
- 각 레플리카 pool active max 3~4 / 30 — **pool 사용률 10%도 안 됨**
- pending 양쪽 모두 0
- MySQL 은 RPS 310 수준에선 여유

### stage6 에서 관찰된 다양한 증상이 모두 CPU 포화의 2차 효과였음 확정

- Redis 캐시 경로 p95 ×16 악화 → ×26 개선으로 **CPU 포화가 원인** 맞음
- pool pending 25 → 0 으로 **pool 포화도 CPU 의 2차 효과** (CPU 대기로 커넥션 점유 시간 길어졌던 것)
- GC 17 ms/s → 7.5 ms/s (합) 로 **GC 도 CPU 압박의 2차 효과**

**즉 stage6 의 "복합 병목" 처럼 보였던 것이 실은 "CPU 단일 병목 + 파급 효과"** 였다. scale-out 으로 CPU 를 풀자 모든 부수 증상이 동반 해소됨.

## 관찰 정리

- **부하 증가 필요성**: VU 3000 은 stage7 아키텍처에 **under-utilize**. "공유 DB 병목" 내러티브를 보려면 VU 를 더 올려야 함
- **Nginx 분배 정상**: 두 인스턴스 CPU 거의 균등 (36% vs 37%). 라운드로빈 작동 확인
- 순수 DB 병목임이 문제임을 확인하기 위해서는 CPU가 어느정도 여유가 있어야 된다는 것을 배울 수 있었음

## 다음 실험 계획

- 모놀리틱 x 2 로 동일하게 가되, VU만 3000 -> 6000 변경해서 테스트 예정

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
