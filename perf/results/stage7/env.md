# stage7 — 환경 기록

## 실험 메타

| 항목 | 값                                                                                                     |
|---|-------------------------------------------------------------------------------------------------------|
| 실험 ID | `stage7`                                                                                              |
| 실험 목적 | stage6 테스트 시 발생했던 CPU 100% 포화에 대한 **스케일 아웃 효과** 및 **공유 DB 병목** 관찰                                  |
| 날짜/시간 | `<2026-04-20 23:33 KST>`                                                                              |
| 선행 실험 | [stage6](../stage6/) — CPU 1.0, VU 3000, pool 30, **CPU 100% 포화 + pool pending 25 + threshold 2개 실패** |

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
- stages: `1m → 200 VU` / `4m → 3000 VU` / `11m (hold 3000)` / `1m → 0 VU`
- 총 소요: 약 17분
- **HikariCP maximum-pool-size**: 30 (각 인스턴스별, 총 60)

## 호스트 사양

stage0 과 동일 — [stage0/env.md](../stage0/env.md) 참조.

## stage6 대비 변경점

| 항목 | stage6 | stage7 |
|---|---|---|
| **레플리카 수** | 1 | **2** |
| **LB** | Nginx → 단일 app | Nginx → 두 app (라운드로빈, Docker DNS) |
| **총 CPU 가용량** | 1.0 | **2.0** (1.0 × 2) |
| **총 메모리** | 512M | **1024M** (512M × 2) |
| **총 HikariCP 연결** | 30 (단일 pool) | **60** (각 레플리카 pool 30) |
| VU 상한 | 3000 | 3000 (동일) |
| stages 구성 | 동일 | 동일 |
| HikariCP maximum-pool-size (레플리카당) | 30 | 30 (동일) |
| app cpus (레플리카당) | 1.0 | 1.0 (동일) |
| app memory (레플리카당) | 512M | 512M (동일) |
