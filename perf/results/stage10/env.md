# stage10 — 환경 기록

## 실험 메타

| 항목 | 값                                                                                         |
|---|-------------------------------------------------------------------------------------------|
| 실험 ID | `stage10`                                                                                 |
| 실험 목적 | `commentClient.count` 제거 → write 서비스 CPU 점유율 감축 효과 측정                                     |
| 날짜/시간 | `2026-04-22 16:01 KST`                                                                    |
| 선행 실험 | [stage9](../stage9/) — read/write 분리 + Kafka, VU 6000, write CPU mean 30.71% |

## 아키텍처

```
[k6] ──▶ [nginx:80] ──┬─ GET /v1/articles*, /v1/hot-articles/*, /v2/comments*  ──▶ [read-app × 2, cpus=1.0/512M each]
                      │                                                                │
                      │                                                                ├──▶ [redis:7.4]   (query model)
                      │                                                                ├──▶ [kafka:3.8]   (consumer)
                      │                                                                └──▶ [write-app]   (cache miss fallback, RestClient)
                      │
                      └─ POST/PUT/DELETE *  &&  default GET                       ──▶ [write-app × 1, cpus=1.0/512M]
                                                                                       │
                                                                                       ├──▶ [mysql:8.0.38] (CRUD + outbox)
                                                                                       ├──▶ [redis:7.4]    (캐시)
                                                                                       └──▶ [kafka:3.8]    (producer via outbox-message-relay)
```

- **app-write**: 1 replica, MySQL/Redis/Kafka 연동, OutboxEventPublisher → Kafka 발행
- **app-read**: 2 replica, Redis query model 우선, miss 시 write-app raw API 호출 (RestClient 4종)
- **Kafka**: KRaft 단일 broker (3.8.0), 4개 토픽 (article/comment/like/view), partition 1, RF 1, AckMode.MANUAL
- **Outbox Pattern**: write 트랜잭션과 atomic하게 outbox 테이블 저장 → MessageRelay가 Kafka 발행

## 리소스 제한

| 서비스 | replicas | cpus | memory | 합계 |
|---|---|---|---|---|
| **write-app** | 1 | 1.0 | 512M | 1.0 CPU / 512M |
| **read-app** | 2 | 1.0 | 512M | 2.0 CPU / 1024M |
| kafka | 1 | (제한 없음) | (제한 없음) | — |
| mysql | 1 | (제한 없음) | (제한 없음) | — |
| redis | 1 | (제한 없음) | (제한 없음) | — |
| nginx | 1 | (제한 없음) | (제한 없음) | — |
| **Spring 합계** | — | — | — | **3.0 CPU / 1.5GB** |

→ stage8 (app×2 = 2.0 CPU / 1.0 GB) 대비 **자원 1.5배 증가**.

## 이미지/버전

- Java: eclipse-temurin:21-jdk
- Spring Boot: 3.3.2
- Spring Kafka: spring-kafka (3.3.2 동반)
- MySQL: 8.0.38
- Redis: 7.4
- Kafka: apache/kafka:3.8.0 (신규)
- Nginx: 1.27-alpine
- Prometheus: v2.55.1, Grafana: 11.3.0, k6: 0.54.0

## 부하 시나리오

- 스크립트: [`../../k6/board-load.js`](../../k6/board-load.js) (현실 시나리오 v2 — stage9에서 단축)
- 트래픽 믹스: NN/g 90-9-1 (read 93% / like 5% / comment 1.5% / article 0.5%)
- stages: `1m → 200 VU` / `2m → 6000 VU` / `4m hold @ 6000` / `30s → 0 VU`
- 총 소요: 약 7.5분 (stage8 18분에서 단축, hold 11m → 4m)
- HikariCP maximum-pool-size: 30 (write-app만, read-app은 JPA 의존 없음)

## stage9 대비 변경점

변수 하나만 바꿈 — **read 측의 write HTTP count 호출 제거**. 아키텍처/리소스 제한/부하 시나리오는 stage9와 동일.

| 항목 | stage9 | stage10 |
|---|---|---|
| `commentClient.count` 호출 | read-app의 `CommentReadService.readAll`·`count`, `ArticleReadService.fetch` 세 경로에서 **매 요청마다** write HTTP 호출 | **read 측 Redis 캐시 우선 조회**, 캐시 miss 시에만 write HTTP 호출 (cold path) |
| 신규 저장소 | — | **`CommentCountQueryRepository`** (commentread 도메인, Redis string, sliding TTL 1일) |
| 신규 이벤트 핸들러 | — | `ArticleCreated` (0 init) / `ArticleDeleted` (DEL) (commentread 도메인) |
| 기존 이벤트 핸들러 변경 | — | `CommentCreated` / `CommentDeleted` 에 count 저장소 갱신 로직 추가 (TTL 리셋) |
| write 엔드포인트 `/v2/comments/articles/{id}/count` | 매 read 요청마다 호출 | **유지** (cold miss rebuild 용) |
| 첫 통합테스트 인프라 | 없음 | `@SpringBootTest @ActiveProfiles("test")` + Redis DB 13 격리 (13 tests) |
| 그 외 (아키텍처/리소스/시나리오/이미지/호스트) | — | **stage9와 동일** |

## 호스트 사양

- 데스크탑 PC: Windows 10 Pro
- CPU: 12 logical cores
- RAM: 16 GB
- Docker Desktop: WSL2 백엔드, RAM 7.7 GB 할당
- JDK: ms-21.0.8

## Git commit

stage10 측정 시점 HEAD: `3e005c3` — `feat: 댓글 카운트 조회로 인한 write 서비스 부하 줄이기 위한 comment count에 대한 Redis 캐싱 처리 추가`
