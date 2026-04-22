# stage11 — 관찰 노트

## 한 줄 요약

viewCount 배치 조회 도입 → **예상과 반대로 오히려 상태가 악화되었음**.

## 한계 지표

| 항목 | stage11 | stage10 |
|---|---|---|
| 한계 RPS (p95<500ms) | **≥551** | ≥549 |
| p50 / p95 / p99 mean | **0.98 / 4.54 / 17.0 ms** | 1.23 / 5.47 / 12.9 ms |
| p99 max | **69.7 ms** | 21.3 ms |
| 실패율 (http_req_failed) | 0.00% | 0.00% |
| iterations | 69,822 | 69,854 |
| k6 thresholds | ✅ 통과 | ✅ |

> p50/p95 mean은 개선, p99 max는 악화

## 리소스 사용 (Grafana Legend)

| 지표 | stage11 mean / max | stage10 mean / max |
|---|---|---|
| CPU write-app | **17.54% / 42.80%** | 11.88% / 35.40% |
| CPU read-app-1 (172.20.0.8) | 26.63% / 53.80% | 29.66% / 72.00% |
| CPU read-app-2 (172.20.0.9) | **34.38% / 69.60%** | 25.32% / 54.40% |
| Kafka consumer lag | 0 / 0 (전 구간) | 0 / 0 |

## 관찰 결과

### 1. write 앱의 CPU 사용량 역으로 증가
11.88 → 17.54%, +5.66%로 증가되었다. 개별 HTTP 호출 제거 효과보다 배치 엔드포인트의 응답 생성·JSON 직렬화 비용이 큰 것으로 추정.

### 2. 게시글 목록 조회(GET /v1/articles) 처리시간도 오히려 악화됨 
요청당 2.43 → 3.37ms / 누적 180s → 250.8s로 증가됨. Redis로의 접근 횟수는 줄였으나 HTTP 왕복 시, 직렬화 오버헤드가 증가하여 그 이득이 상쇄된 것으로 파악됨. 페이징은 10개 처리 수준이고, 캐싱 처리도 되어 있기 때문으로 추정

| # | 가설 | 결과 |
|---|---|---|
| H1 | `GET /v1/articles` 요청당 ≤ 1.0 ms | ❌ **3.37 ms** (stage10 2.43 ms 대비 +39% 악화) |
| H2 | `GET /v1/articles` 누적 ≤ 80 s | ❌ **250.8 s** (stage10 180 s 대비 +39% 악화) |
| H3 | read-app CPU −3~5%p | ❌ read-app-1 −3%p / **read-app-2 +9%p** (순 증가) |
| H4 | 실패율 0% | ✓ 0% |
| H5 | write 측 count 호출 유사 수준 유지 | ❌ 개별 엔드포인트 12K (stage10 6K 대비 2배) |

## 다음 실험 계획

배치처리 이전으로 롤백 후 최적화 방향 재설계 예정.

---

체크리스트 (RUN.md §7-10):
- [x] env.md / k6-summary.json / k6-console.txt
- [x] grafana-{overview, latency, timeshare, endpoint, cpu, kafka-lag}.png (6장)
- [x] ../README.md 요약 표 갱신 (이 커밋에 포함)
