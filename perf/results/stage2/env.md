# stage2 — 환경 기록

## 실험 메타

| 항목 | 값                                              |
|---|------------------------------------------------|
| 실험 ID | `stage2`                                       |
| 실험 목적 | VU 200으로 늘린 후, 부하 재테스트                         |
| 날짜/시간 | `<2026-04-20 12:49 KST>`                       |
| 선행 실험 | [stage1](../stage1/) — CPU 1.0, VU 100, 한계 미도달 |

## 아키텍처

```
[k6 container] ──▶ [nginx:80] ──▶ [app × 1 replica, cpus=1.0, mem=512M]
                                        │
                                        ├──▶ [mysql:8.0.38]
                                        └──▶ [redis:7.4]
```

- **레플리카 수**: 1 (모놀리틱 단일 인스턴스)
- **LB**: Nginx 경유
- **DB**: 컨테이너 MySQL, 볼륨 `mysql-data`
- **캐시**: 컨테이너 Redis (휘발성)

## 리소스 제한

| 서비스 | cpus | memory |
|---|---|---|
| app | **1.0** | 512M |
| mysql | (제한 없음) | (제한 없음) |
| redis | (제한 없음) | (제한 없음) |
| nginx | (제한 없음) | (제한 없음) |

## 이미지/버전

stage1 과 동일 — [stage1/env.md](../stage1/env.md) 참조.

## 부하 시나리오

- 스크립트: [`../../k6/board-load.js`](../../k6/board-load.js)
- 트래픽 믹스 근거: [`../../k6/METHODOLOGY.md`](../../k6/METHODOLOGY.md) (NN/g 90-9-1)
- stages: `30s → 50 VU` / `1m → 200 VU` / `2m (hold 200)` / `30s → 0 VU`
- **좋아요 전략**: `pessimistic-lock-1` (원자적 UPDATE — stage1 까지는 `pessimistic-lock-2` 사용)
- 총 소요: 약 4분

## 호스트 사양

stage0 과 동일 — [stage0/env.md](../stage0/env.md) 참조.

## stage1 대비 변경점

| 항목 | stage1 | stage2 |
|---|---|---|
| **VU 상한** | 100 | **200** |
| app cpus | 1.0 | 1.0 (동일) |
| app memory | 512M | 512M (동일) |
| 레플리카 | 1 | 1 (동일) |

