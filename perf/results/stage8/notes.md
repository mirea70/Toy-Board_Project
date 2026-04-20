# stage8 — 관찰 노트

> **부하 극한 탐색**. stage7 (VU 3000) 에서 여유있던 자원을 최대한 소진시키면 어떻게 되는지 확인.
> 모노 ×2 + pool 30 유지, **VU 3000 → 6000** 만 변경.

## 결과 요약

- RPS 선형 확장 성공: **310 → 604** (VU 2배 ↔ RPS 2배)
- CPU 재포화 임박: 인스턴스별 max **99.8% / 93.8%**
- 공유 DB 병목은 미발생
- 서버 자원의 약 **90% 이상이 읽기 기능에 소비**

## 측정 데이터

### k6 summary

| 항목 | 값 |
|---|---|
| 한계 RPS (p95 < 500ms) | ≥604 (미도달, threshold 전부 ✓) |
| p50 / p95 / p99 max | 4.31ms / 59.4ms / 757ms |
| http_reqs / iterations | 658,983 / 186,702 (+3,841 interrupted) |
| 실패율 | 0.00% (0 / 658,983) |

### 시스템 리소스 (stage7 max ↔ stage8 max)

| 지표 | stage7 | stage8 |
|---|---|---|
| `process_cpu_usage` (per 인스턴스) | 62.2% / 55.0% | **99.8% / 93.8%** |
| `jvm_gc_pause_seconds` rate (합) | 7.5 ms/s | ~13 ms/s |
| `hikaricp_active` (per 인스턴스) | 3 / 4 | 21 / 18 (of 30) |
| `hikaricp_pending` | 0 / 0 | **0 / 0** |

### URI 별 요청 / 서버 시간 분포

[grafana-count.png](grafana-count.png), [grafana-timeshare.png](grafana-timeshare.png) 기준.

| method + URI | 누적 건수 | 서버 시간 Mean (s/s) | 분류 |
|---|---|---|---|
| GET /v1/articles | 191 K | 1.73 | 읽기 |
| GET /v1/articles/{articleId} | 191 K | 0.84 | 읽기 |
| GET /v2/comments | 190 K | 1.19 | 읽기 |
| GET /v1/hot-articles/.../dateStr | 67 K | 1.04 | 읽기 |
| POST /v1/article-likes/.../pessimistic-lock-1 | 9 K | 0.22 | 쓰기 |
| DELETE /v1/article-likes/.../pessimistic-lock-1 | 9 K | 0.23 | 쓰기 |
| POST /v2/comments | 3 K | 0.08 | 쓰기 |
| POST /v1/articles | 0.96 K | 0.02 | 쓰기 |
| **읽기 합계** | **639 K (96.7%)** | **4.80 (89.7%)** | |
| **쓰기 합계** | **22 K (3.3%)** | **0.55 (10.3%)** | |

## 분석 / 결론

### ① 공유 DB 병목이 안 생긴 이유

- 읽기 기능는 이미 **Redis 쿼리 모델** 로 서빙됨 → MySQL 거의 안 탐
- 쓰기 기능은 워낙 요청률이 적어 MySQL 부담 미미할 수밖에 없음

### ② 트래픽의 대부분과 CPU 점유의 대부분은 읽기 기능이 사용 중

- **CPU 점유시간의 89.7% / 요청건수의 96.7% 가 읽기 기능!**
- 전체 앱을 scale-out 하면 쓰기 기능을 위한 자원까지 불필요하게 낭비된다고 판단

### ③ **읽기 서비스 독립 분리 후 전용 수평 확장하기로 결정**

- 쓰기 인스턴스는 최소화하고, 읽기 인스턴스만 분리하여 확장하면 VU 증가에 대해 자원 효율이 대폭 개선될 것으로 보임
- 지금까지 stage5~8 에서 scale-out 후 DB pool 이 문제된 적 없음 (쓰기 DB 경로 자체가 가볍다는 증거)

## 다음 실험 계획

**stage9: article-read 를 독립 프로세스로 분리 + 수평 확장**

```
nginx
 ├── GET /v1/articles, /v1/articles/{id}, /v1/hot-articles/..., /v2/comments
 │     → [article-read × N] ──▶ Redis
 └── POST/DELETE/PUT 전부
       → [article-write × 1~2] ──▶ MySQL ──(이벤트)──▶ article-read
```

---

## 체크리스트

- [x] `env.md` 작성 (환경 고정)
- [x] `k6-summary.json` 저장
- [x] `k6-console.txt` 저장
- [x] `grafana-overview.png` 저장
- [x] `grafana-latency.png` 저장
- [x] `grafana-hikari.png` 저장
- [x] `grafana-gc.png` 저장
- [x] `grafana-timeshare.png` 저장 (method+URI 별 서버 시간 점유)
- [x] `grafana-count.png` 저장 (method+URI 별 누적 요청 건수)
- [x] `../README.md` 요약 표에 한 줄 갱신
