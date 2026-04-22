# stage10 — 관찰 노트

## 한 줄 요약

`commentClient.count` 제거 → write CPU **-61%** (30.71 → 11.88%), read CPU까지 **-42%** (47 → 27%)

## 한계 지표

| 항목 | stage10 | stage9 |
|---|---|---|
| 한계 RPS (p95<500ms) | **≥549** | ≥550 |
| p50 / p95 / p99 max | **1.23 / 5.47 / 21.3 ms** | 2.79 / 19.1 / 757 ms |
| 실패율 (http_req_failed) | **0.00%** | 0.00% |
| iterations | 69,854 | — |
| k6 thresholds | ✅ 통과 | ✅ |

> p50/p95/p99 수치는 Grafana Latency 패널 평균값 기준. k6 summary 기준 p95=7.45ms

## 리소스 사용 (Grafana Legend)

| 지표 | stage10 mean / max | stage9 mean / max |
|---|---|---|
| CPU write-app | **11.88% / 35.40%** | 30.71% / 74.00% |
| CPU read-app-1 (172.20.0.8) | **29.66% / 72.00%** | 44.32% / 76.80% |
| CPU read-app-2 (172.20.0.9) | **25.32% / 54.40%** | 50.75% / 82.80% |
| Kafka consumer lag | **0 / 0** (전 구간, 모든 group·topic) | 0 / 0 |

## 관찰 결과

- stage9에서 문제 삼았던 "쓰기 API 사용률 대비 Write CPU 사용률이 너무 높던 문제"가 예상보다 크게 개선됨.
- **예상 외 효과**: read-app CPU까지 내려감 (47→27%). HTTP 요청 자체가 감소된 효과로 추정된다.
- **p99 max 값도 크게 개선됨** (757 → 21.3 ms). stage8~9 내내 꼬리 분포에 지속적으로 찍히던 큰 latency 문제가 함께 해소되었음.
- `댓글 수 API` 호출량: 기존 72K 수준 -> **358건**으로 감소됨. 약 99% 감소. 이를 통해 캐싱 처리가 잘 되었음을 확인 가능
- 엔드포인트별 누적 CPU 사용량을 보면 `GET /v1/articles` 즉 게시글 목록조회가 압도적으로 많다. 요청 당 평균 처리시간은 2.4ms 수준으로 그리 높은 편은 아니나, 최적화할 수 있는 여지가 있다면 적용해보는 게 좋을 것 같음!

| #  | 가설 | 결과 |
|----|---|---|
| H1 | write-app CPU mean ≤ 22% | ✓ **11.88%** (목표 약 2배 초과) |
| H2 | read-app CPU 동등 또는 미미 증가 (+5%p 이내) | ✓ **반대로 감소** (47 → 27%) |
| H3 | p95 ≤ 19.1 ms | ✓ **5.47 ms mean / 11.3 ms max** |
| H4 | 실패율 0% | ✓ 0% |
| H5 | `/v2/comments/.../count` 호출량 −99% 이상 | ✓ 측정 구간 358건 (stage9 대비 비율 0.2~0.5%) |

## 다음 실험 계획

`GET /v1/articles` 최적화** — 게시글 조회 데이터를 1초만 캐싱하고 각 게시글마다 write 앱에 요청 중인데 이를 배치처리하면 최적화 가능할 것으로 보여 적용해볼 예정

---

체크리스트 (RUN.md §7-10):
- [x] env.md / k6-summary.json / k6-console.txt
- [x] grafana-{overview, latency, timeshare, count, cpu, kafka-lag}.png (6장)
- [x] ../README.md 요약 표 갱신 (이 커밋에 포함)
