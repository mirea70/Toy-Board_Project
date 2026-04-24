# stage14 — 환경 기록

## 실험 메타

| 항목 | 값                                                                                                                          |
|---|----------------------------------------------------------------------------------------------------------------------------|
| 실험 ID | `stage14`                                                                                                                  |
| 실험 목적 | stage13 에서 관찰된 **호스트 전체 CPU 포화** 변수를 제거. 서버 호스트를 Windows 데스크탑 → macOS 로 옮기고, k6 부하 생성기를 **Windows 데스크탑으로 외부화**하여 서버 자원을 온전히 Spring 3-컨테이너에 할당한 상태에서 stage13 과 동일 조건 (VU 12000) 재측정 |
| 날짜/시간 | `2026-04-23 23:48:30 KST` |
| 선행 실험 | [stage13](../stage13/) — read 도메인 분리 (article-read / sub-read / write-app), p99 mean 119ms / max 559ms, article-read CPU max 100%, 호스트 전체 CPU 포화 관찰으로 자원 분배 불확실성 잔존 |

## 아키텍처

```
[k6 (Windows 데스크탑, WiFi 동일 subnet)] ──▶ [nginx:8080 (macOS host)] ──┬─ GET /v1/articles*                ──▶ [article-read × 1, cpus=1.0/512M]
                                                                          │                                                │
                                                                          ├─ GET /v1/hot-articles/*, /v2/comments*  ──▶ [sub-read × 1, cpus=1.0/512M]
                                                                          │                                                │
                                                                          │                                                ├──▶ [redis:7.4]   (query model, 공유)
                                                                          │                                                ├──▶ [kafka:3.8]   (독립 consumer group)
                                                                          │                                                └──▶ [write-app]   (cache miss fallback)
                                                                          │
                                                                          └─ POST/PUT/DELETE *  &&  default GET       ──▶ [write-app × 1, cpus=1.0/512M]
                                                                                                                           │
                                                                                                                           ├──▶ [mysql:8.0.38]
                                                                                                                           ├──▶ [redis:7.4]
                                                                                                                           └──▶ [kafka:3.8]
```

- 서비스 구성은 stage13 과 동일 (`article-read` / `sub-read` / `write-app`, 각 1 replica).
- **변화는 실행 호스트뿐**: Spring + infra 전체를 macOS(Docker Desktop) 로 이동, k6 는 Windows 데스크탑에서 외부 실행.
- k6 와 서버는 동일 WiFi subnet 에서 통신.

## 리소스 제한

| 서비스 | replicas | cpus | memory | 합계 |
|---|---|---|---|---|
| **write-app** | 1 | 1.0 | 512M | 1.0 CPU / 512M |
| **article-read** | 1 | 1.0 | 512M | 1.0 CPU / 512M |
| **sub-read** | 1 | 1.0 | 512M | 1.0 CPU / 512M |
| kafka | 1 | (제한 없음) | (제한 없음) | — |
| mysql | 1 | (제한 없음) | (제한 없음) | — |
| redis | 1 | (제한 없음) | (제한 없음) | — |
| nginx | 1 | (제한 없음) | (제한 없음) | — |
| **Spring 합계** | — | — | — | **3.0 CPU / 1.5GB** |

→ stage13 과 동일.

## 이미지/버전

- Java: eclipse-temurin:21-jdk
- Spring Boot: 3.3.2
- Spring Kafka: spring-kafka (3.3.2 동반)
- MySQL: 8.0.38
- Redis: 7.4
- Kafka: apache/kafka:3.8.0
- Nginx: 1.27-alpine
- Prometheus: v2.55.1, Grafana: 11.3.0, k6: 0.54.0

## 부하 시나리오

- 스크립트: [`../../k6/board-load.js`](../../k6/board-load.js) (현실 시나리오 v2)
- 트래픽 믹스: NN/g 90-9-1 (read 93% / like 5% / comment 1.5% / article 0.5%)
- stages: `1m → 200 VU` / `2m → 12000 VU` / `4m hold @ 12000` / `30s → 0 VU`
- 총 소요: 약 7.5분
- HikariCP maximum-pool-size: 30 (write-app만)

## stage13 대비 변경점

변수 하나 — 실행 호스트. 아키텍처 / 리소스 / 트래픽 믹스 / VU 상한 모두 동일.

| 항목 | stage13                                                            | stage14                                                                      |
|---|--------------------------------------------------------------------|------------------------------------------------------------------------------|
| 서버 호스트 | Windows 10 Pro / 12 logical cores / RAM 16GB / Docker Desktop WSL2 | **macOS M1 / CPU 8 cores (P-core 4 + E-core 4) / RAM 16GB / Docker Desktop** |
| k6 실행 위치 | 외부 Mac OS                                                          | **외부 Windows 데스크탑**                                                          |
| 서비스 구성 | article-read ×1 + sub-read ×1 + write-app ×1                       | **동일**                                                                       |
| 각 컨테이너 리소스 | 1.0 CPU / 512M                                                     | **동일**                                                                       |
| 총 리소스 | 3.0 CPU / 1.5GB                                                    | **동일**                                                                       |
| VU 상한 | 12000                                                              | **동일**                                                                       |
| 애플리케이션 로직 | read 도메인 분리                                                        | **동일**                                                                       |
| k6 스크립트 | board-load.js (create article이 `if (lastArticleId)` 내부)            | **create article 분기 외부 이동 (`board-load.js:155-170`)**                        |

## 호스트 사양

- **서버 호스트**: **macOS M1 / CPU 8 cores (P-core 4 + E-core 4) / RAM 16GB / Docker Desktop**
- **k6 실행 호스트**: Windows 10 Pro / CPU 12 logical cores / RAM 16 GB (stage13 과 동일 머신)

## Git commit

stage14 측정 시점 HEAD: `d4b518b` — `refactor: nginx 설정 변경` (+ 본 실험 중 board-load.js 패치)
