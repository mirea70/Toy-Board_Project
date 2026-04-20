# stage3 — 환경 기록

## 실험 메타

| 항목 | 값                                                         |
|---|-----------------------------------------------------------|
| 실험 ID | `stage3`                                                  |
| 실험 목적 | 시나리오 Sleep 시간 수정 및 VU 800으로 늘린 후, 부하 재테스트                 |
| 날짜/시간 | `<2026-04-20 15:49 KST>`                                  |
| 선행 실험 | [stage2](../stage2/) — CPU 1.0, VU 200, CPU 81%, DB 병목 예상 |

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
- 트래픽 믹스 근거: [`../../k6/METHODOLOGY.md §2~3`](../../k6/METHODOLOGY.md) (NN/g 90-9-1)
- **Think-time 시나리오**: 현실 (iter_dur ~27s). 근거: [`METHODOLOGY.md §4`](../../k6/METHODOLOGY.md)
    - stage0~2 는 **압축** 시나리오 (iter_dur ~4.6s) 였음 → stage3 부터 전환
- stages: `1m → 200 VU` / `2m → 800 VU` / `11m (hold 800)` / `1m → 0 VU`
- 총 소요: 약 15분

## 호스트 사양

stage0 과 동일 — [stage0/env.md](../stage0/env.md) 참조.

## stage2 대비 변경점

| 항목 | stage2 | stage3 |
|---|---|---|
| **Think-time 시나리오** | 압축 (iter_dur 4.6s) | **현실 (iter_dur 27s)** |
| **VU 상한** | 200 (압축 기준) | **800 (현실 기준, stage2 와 VU 비교 불가)** |
| stages 구성 | `30s→50 / 1m→200 / 2m hold / 30s→0` (총 4분) | `1m→200 / 2m→800 / 11m hold / 1m→0` (총 15분) |
| app cpus | 1.0 | 1.0 (동일) |
| app memory | 512M | 512M (동일) |
| 레플리카 | 1 | 1 (동일) |
