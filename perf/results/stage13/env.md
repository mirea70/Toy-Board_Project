# stage13 — 환경 기록

## 실험 메타

| 항목 | 값                                                                                                                          |
|---|----------------------------------------------------------------------------------------------------------------------------|
| 실험 ID | `stage13`                                                                                                                  |
| 실험 목적 | read 서비스를 article-read / sub-read 2개로 도메인 분리 + write (총 3개) 후 stage12 와 동일 조건 (VU 12000) 측정                                  |
| 날짜/시간 | `2026-04-23 17:55 KST`                                                                                                     |
| 선행 실험 | [stage12](../stage12/) — read-app ×2 (단일 모듈), p99 mean 194ms / max 1.49s, read-app CPU max 100%, Tomcat busy 지속 포화, 실패율 2.53% |

## 아키텍처

```
[k6 (Mac OS, WiFi 동일 subnet)] ──▶ [nginx:8080(host)] ──┬─ GET /v1/articles*                ──▶ [article-read × 1, cpus=1.0/512M]
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

- **app-article-read**: 1 replica, `/v1/articles*` 전담, Kafka consumer group `toy-board-app-article-read`
- **app-sub-read**: 1 replica, `/v1/hot-articles/*` + `/v2/comments*` 전담, Kafka consumer group `toy-board-app-sub-read`
- **app-write**: 1 replica (기존과 동일)
- **공유 상태**: `ArticleQueryModel`, `ArticleQueryModelRepository`, `CommentCountQueryRepository`, `OptimizedCache` 인프라, 4개 HTTP client 는 `common:read-common` 모듈로 승격해 공유

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

→ stage12 와 동일 (단 read-app ×2 → article-read + sub-read 로 구성 변경).

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

## stage12 대비 변경점

변수 하나 — read 모듈 도메인 분리. 애플리케이션 로직 / 리소스 총량 / 트래픽 믹스 / k6 위치 / VU 상한 모두 동일.

| 항목 | stage12 | stage13 |
|---|---|---|
| 서비스 구성 | read-app ×2 (단일 모듈) | **article-read ×1 + sub-read ×1** |
| Gradle 모듈 | `service:app-read` (1개) | **`common:read-common` + `service:app-article-read` + `service:app-sub-read`** |
| Kafka consumer group | `toy-board-app-read` | **`toy-board-app-article-read` + `toy-board-app-sub-read` (독립)** |
| nginx 라우팅 | 2-way (read-app / write-app) | **3-way (article-read / sub-read / write-app), path 기반** |
| 각 컨테이너 리소스 | 1.0 CPU / 512M | **동일** |
| 총 리소스 | 3.0 CPU / 1.5GB | **동일** |
| k6 실행 위치 / VU 상한 | 외부 Mac OS / 12000 | **동일** |
| 애플리케이션 로직 | stage10 시점 | **동일 (파일 이동 + 패키지 rename 외 로직 변경 없음)** |

## 호스트 사양

- **서버 호스트 (데스크탑)**: Windows 10 Pro / CPU 12 logical cores / RAM 16 GB / Docker Desktop WSL2, WSL RAM 7.7 GB / JDK ms-21.0.8

## Git commit

stage13 측정 시점 HEAD: `c88953a` — `refactor: read 서비스 분리`
