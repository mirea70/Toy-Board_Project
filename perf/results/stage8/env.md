# stage8 — 환경 기록

## 실험 메타

| 항목 | 값                                                                                        |
|---|------------------------------------------------------------------------------------------|
| 실험 ID | `stage8`                                                                                 |
| 실험 목적 | VU 3000 → **6000** 증가시켜 인스턴스가 증가된 상황에서 병목 지점을 탐색하고 개선점을 찾기 위함                            |
| 날짜/시간 | `<2026-04-21 00:42 KST>`                                                                 |
| 선행 실험 | [stage7](../stage7/) — 모노 ×2, VU 3000, pool 30, 모든 조건 pass (CPU 각 ~37%, pool active 3~4) |

## 아키텍처

```
[k6 container] ──▶ [nginx:80] ──┬──▶ [app × 2 replicas, cpus=1.0, mem=512M (each)]
                                │      │
                                │      ├──▶ [mysql:8.0.38]  ← 공유
                                │      └──▶ [redis:7.4]     ← 공유
                                └── round-robin via Docker DNS
```

- **레플리카 수**: **2** (모놀리틱 수평 확장)
- **LB**: Nginx 경유
- **DB**: 컨테이너 MySQL, 볼륨 `mysql-data`
- **캐시**: 컨테이너 Redis (휘발성)

## 리소스 제한

| 서비스 | cpus | memory |
|---|---|---|
| app | **1.0 × 2 인스턴스 (총 2.0)** | 512M × 2 (총 1GB) |
| mysql | (제한 없음) | (제한 없음) |
| redis | (제한 없음) | (제한 없음) |
| nginx | (제한 없음) | (제한 없음) |

## 이미지/버전

stage1 과 동일 — [stage1/env.md](../stage1/env.md) 참조.

## 부하 시나리오

- 스크립트: [`../../k6/board-load.js`](../../k6/board-load.js)
- 트래픽 믹스 근거: [`../../k6/METHODOLOGY.md §2~3`](../../k6/METHODOLOGY.md) (NN/g 90-9-1)
- stages: `1m → 200 VU` / `5m → 6000 VU` / `11m (hold 6000)` / `1m → 0 VU`
- 총 소요: 약 18분
- **HikariCP maximum-pool-size**: 30 (각 인스턴스별, 총 60)

## 호스트 사양

stage0 과 동일 — [stage0/env.md](../stage0/env.md) 참조.

## stage7 대비 변경점

| 항목 | stage7 | stage8 |
|---|---|---|
| **VU 상한** | 3000 | **6000** |
| stages 구성 | `1m→200 / 4m→3000 / 11m hold / 1m→0` (총 17분) | `1m→200 / 5m→6000 / 11m hold / 1m→0` (총 **18분**) |
| 레플리카 수 | 2 | 2 (동일) |
| HikariCP maximum-pool-size | 30 | 30 (동일) |
| app cpus / memory | 1.0 / 512M × 2 | 1.0 / 512M × 2 (동일) |
