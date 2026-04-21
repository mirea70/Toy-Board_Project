# stage9 — 환경 기록

## 실험 메타

| 항목 | 값                                                                                      |
|---|----------------------------------------------------------------------------------------|
| 실험 ID | `stage9`                                                                               |
| 실험 목적 | 읽기 서비스 분리 후 스케일 아웃의 효과 측정                                                              |
| 날짜/시간 | `2026-04-22 02:41 KST`                                                                 |
| 선행 실험 | [stage8](../stage8/) — 모놀리스 ×2, VU 6000, 한계 RPS ≥604 (선형 확장), CPU 99.8%/93.8% (재포화 임박) |

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

## stage8 대비 변경점

| 항목 | stage8 | stage9 |
|---|---|---|
| **아키텍처** | 모놀리스 ×2 | **read/write 모듈 분리** + Kafka + Outbox + RestClient |
| Spring 인스턴스 합계 자원 | 2.0 CPU / 1.0 GB | **3.0 CPU / 1.5 GB** (1.5배) |
| read 트래픽 라우팅 | 모든 인스턴스 round-robin | **nginx에서 GET prefix 별도 라우팅 → read-app** |
| write 트래픽 라우팅 | 모든 인스턴스 round-robin | nginx default → write-app |
| 이벤트 전파 | `ApplicationEventPublisher` (인메모리) | **Kafka + Outbox Pattern** (트랜잭션 일관성, at-least-once) |
| Comment query model | 없음 (MySQL 직접) | **신설** (Redis ZSET + per-comment hash) |
| Article cache miss fallback | (모놀리스라 무관) | **RestClient → write-app raw API** |
| MySQL schema | 비즈니스 테이블만 | **`outbox` 테이블 추가** |
| `articleCount` (응답 필드) | 응답에 포함 | **제거** (게시판 1개라 dead data) |
| 응답 ID 직렬화 | Long → JSON number | **String** (`spring.jackson.generator.write-numbers-as-strings: true`) — JS Number 정밀도 손실 회피 |
| read-app 시작 시 cache | 비어있음 | **prepopulator** — 모든 article을 query model + ZSET에 prepopulate |
| HotArticleService.readAll | (해당 없음) | batch query model read + cache miss 시에만 client (N+1 회피) |
| VU 상한 | 6000 | 6000 (동일) |
| 부하 측정 시간 | 18분 (hold 11m) | **7.5분 (hold 4m)** |

## 호스트 사양

- 데스크탑 PC: Windows 10 Pro
- CPU: 12 logical cores
- RAM: 16 GB
- Docker Desktop: WSL2 백엔드, RAM 7.7 GB 할당
- JDK: ms-21.0.8

## Git commit

stage9 측정 시점 HEAD: 사용자 직접 commit (Task 25 반영, 부하 측정 직전)
