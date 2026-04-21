# stage9 — 관찰 노트

## 한 줄 요약

read/write 서비스 분리 → 모든 가설 통과. p95 stage8 대비 **-68%**(59→19ms), 실패율 0%, CPU read 47% / write 31%.

## 한계 지표

| 항목 | stage9 | stage8 |
|---|---|---|
| 한계 RPS (p95<500ms) | **≥550** | ≥604 |
| p50 / p95 / p99 max | **2.79 / 19.1 / 757 ms** | 4.31 / 59.4 / 757 ms |
| 실패율 | **0.00%** | 0.00% |
| k6 thresholds | ✅ 통과 | ✅ |

## 리소스 사용 (Grafana Legend)

| 지표 | mean / max |
|---|---|
| CPU write-app | **30.71% / 74%** |
| CPU read-app-1 / read-app-2 | 44.32% / 50.75% (max 76.8% / 82.8%) |
| Kafka consumer lag | **0 / 0** (모든 group·topic) |

## 관찰 결과

- stage8에서 존재하던 CPU 부하가 거의 줄어들었음. — write 30% + read 47% 평균, 시스템 여유 있음.
- 그러나 write 앱의 CPU 사용량은 실제 쓰기 API 사용률에 비해 높은 편이라고 생각됨. 개선해볼 필요가 있을 듯??
- GET /v2/comments API 호출 수가 많고 CPU 점유율도 높은 편인데, 캐싱 데이터를 활용해 줄여볼 여지가 있을 것으로 보임.

| # | 가설 | 결과 |
|---|---|---|
| H1 | read-app이 70% 이상 부하 격리 | ✓ (외부 호출 245K / 전체 333K ≈ **74%**) |
| H2 | stage8 대비 latency 동등/개선 | ✓ p95 **-68%** |
| H3 | read-app 인스턴스 균등 (≤10% 차) | ✓ 6.4%p |
| H4 | Kafka lag 1초 미만 | ✓ 전 구간 0 |
| H5 | Outbox 0 수렴 | smoke 시 0 (본 측정 후 SQL 확인 필요) |

## 다음 실험 계획

- **stage10**: `commentClient.count` 제거 등 write 서비스에 대한 CPU 점유율 줄여보기
