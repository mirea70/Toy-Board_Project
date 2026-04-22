# stage11 — 환경 기록

## 실험 메타

| 항목 | 값                                                                                                                   |
|---|---------------------------------------------------------------------------------------------------------------------|
| 실험 ID | `stage11`                                                                                                           |
| 실험 목적 | `GET /v1/articles` 의 viewCount 조회 배치 처리 (N+1 제거) → `GET /v1/articles` 최적화 효과 측정                                     |
| 날짜/시간 | `2026-04-22 18:00 KST`                                                                                              |
| 선행 실험 | [stage10](../stage10/) — 댓글 count Redis 캐싱, VU 6000, write CPU mean 11.88%, `GET /v1/articles` 누적 180s / 요청당 2.43ms |

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

## stage10 대비 변경점

변수 하나만 바꿈 — **`ArticleReadService.readAll` 의 viewCount 조회를 개별 호출 → 배치 호출로 전환**. 아키텍처/리소스 제한/부하 시나리오는 stage10과 동일.

| 항목 | stage10 | stage11 |
|---|---|---|
| `viewCountQueryService.count()` 호출 형태 | 페이지 내 각 article 마다 **개별 호출** N회 (Redis GET × N) | **배치 1회** (`countAll(List<articleId>)` → Redis MGET) |
| write 엔드포인트 (viewCount) | `GET /v1/article-views/articles/{id}/count` (단일) | **신규 추가**: `GET /v1/article-views/articles/count?articleIds=1,2,3` (배치), 기존 단일 엔드포인트 유지 |
| read 측 캐시 | `@OptimizedCacheable` 의 `articleViewCount::{id}` (`OptimizedCache` wrapper, 논리/물리 TTL 분리) | **신규 namespace** `articleViewCountBatch::{id}` (plain Long string, TTL 1초). 기존 namespace와 **분리** |
| Cache stampede 보호 | `OptimizedCacheLockProvider` 적용 (기존 경로) | **없음** (신규 배치 경로) |
| `ArticleReadService.readAll` | stream 내부에서 각 article 마다 `viewCountQueryService.count(id)` | 진입 직후 `viewCountQueryService.countAll(ids)` 1회, 결과 Map 재사용 |
| 통합 테스트 | 13개 | **18개** (`ViewCountQueryServiceTest` 5개 추가: empty / all miss / all hit / partial miss / incomplete response) |
| 그 외 (아키텍처/리소스/시나리오/이미지/호스트) | — | **stage10과 동일** |

## 호스트 사양

- 데스크탑 PC: Windows 10 Pro
- CPU: 12 logical cores
- RAM: 16 GB
- Docker Desktop: WSL2 백엔드, RAM 7.7 GB 할당
- JDK: ms-21.0.8

## Git commit

stage11 측정 시점 HEAD: `7441550` — `refactor: 게시글 목록 조회 기능의 조회수 데이터 캐시 미스 시, 배치로 가져오도록 처리하여 최적화`
