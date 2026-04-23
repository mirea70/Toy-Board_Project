# <STAGE_ID> — 관찰 노트

## 한 줄 요약

<!-- 진짜 한 문장. 무엇이 일어났고 무엇이 병목인지.
     예: "DB 풀 30 → pending 95~171, write-app CPU 100% — read-app cache miss로 cascading fallback이 원인." -->

## 한계 지표

| 항목 | 값 | 비고 |
|---|---|---|
| 한계 RPS (p95<500ms) | _N req/s_ | |
| p50 / p95 / p99 | _N / N / Nms_ | |
| 실패율 (http_req_failed) | _N%_ | |
| iterations | _N_ | |
| k6 thresholds | ✅ pass / ❌ fail | 실패 시 어느 항목 |

## 리소스 사용 (Grafana Legend 숫자)

| 지표 | mean / max / last | 관찰 |
|---|---|---|
| `process_cpu_usage` | _N / N / N_ | 인스턴스별 분담, 포화 여부 |
| `hikaricp_connections_active` / `pending` | _N / N_ | pending 0 이상이면 DB 병목 |
| `jvm_gc_pause_seconds` rate | _N ms/s_ | p99 튐과 시간 겹침? |
| `kafka_consumer_fetch_manager_records_lag_max` | _N records_ | 0 수렴이면 정상 |

## 관찰 결과

<!-- 병목 분석 + 가설별 결과 + 학습을 한 섹션에 (bullet + 표).

     각 bullet은 "**핵심 관찰 한 줄**: 수치/근거 — 해석" 형태로. bold 리드만 훑어도 요지가 잡히게.
     예:
     - **write CPU 역으로 증가** (11.88 → 17.54%, +5.66%p): 배치 엔드포인트 응답 직렬화 비용이 개별 호출 제거 효과를 상회한 것으로 보임.
     - **p99 max 악화** (21 → 70 ms): hold 구간 스파이크. 배치 캐시 stampede 의심.

     가설 검증은 아래 표로 한 번에:

| # | 가설 | 결과 |
|---|---|---|
| H1 | ... | ✓ / ❌ / △ + 근거 |

     놀라운 발견 / 핵심 학습이 있으면 bullet 하나로 섞어 넣기. 별도 섹션 금지.
-->

## 다음 실험 계획

<!-- 이 결과로 자연스럽게 이어지는 다음 stage 후보. 생략 가능. -->

---

체크리스트 (RUN.md §7-10 따름):
- [ ] env.md / k6-summary.json / k6-console.txt
- [ ] grafana-{overview, latency, timeshare, count, cpu-split, kafka-lag}.png (6장)
- [ ] ../README.md 요약 표 갱신
