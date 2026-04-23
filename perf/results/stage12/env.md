# stage12 — 환경 기록

## 실험 메타

| 항목 | 값                                                                                                                          |
|---|----------------------------------------------------------------------------------------------------------------------------|
| 실험 ID | `stage12`                                                                                                                  |
| 실험 목적 | VU 12000으로 부하를 올려 이후 개선 방향 체크                                                                                              |
| 날짜/시간 | `2026-04-23 12:15 KST`                                                                                                     |
| 선행 실험 | [stage10](../stage10/) — 댓글 count Redis 캐싱, VU 6000, write CPU mean 11.88%, p95 5.47ms (stage11 은 회귀로 롤백되어 비교 대상은 stage10) |

## 아키텍처

```
[k6 (Mac OS, WiFi 동일 subnet)] ──▶ [nginx:8080(host)] ──┬─ GET /v1/articles*, /v1/hot-articles/*, /v2/comments*  ──▶ [read-app × 2, cpus=1.0/512M each]
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

- **k6**: stage11 까지는 데스크탑 컨테이너 내부에서 실행하였으며, 데스크탑의 메모리 압박 회피 목적으로 stage12 부터 **외부 Mac OS** 에서 부하 실행
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

→ stage10과 동일.

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
- HikariCP maximum-pool-size: 30 (write-app만, read-app은 JPA 의존 없음)

## stage10 대비 변경점

변수 두 가지 동시 변경 — 부하 생성기 위치 + VU 상한. 애플리케이션 코드 / 리소스 제한 / 트래픽 믹스 동일.

| 항목 | stage10 | stage12 |
|---|---|---|
| k6 실행 위치 | 데스크탑 Docker 컨테이너 (`toy-board-k6`, 내부 network) | **외부 노트북 Docker** (WiFi 동일 subnet, `BASE_URL=http://<desktop-ip>:8080`) |
| 네트워크 경로 | Docker bridge (loopback 수준 RTT) | **WiFi LAN** (RTT 2~5ms, jitter 존재) |
| 부하 VU 상한 | 6000 | **12000** (2배) |
| 트래픽 시나리오 | board-load.js v2 | **동일** (페이지 uniform, 믹스 93/5/1.5/0.5) |
| 애플리케이션 코드 | stage10 시점 | **동일** (stage11 배치 최적화는 롤백 후 측정) |
| 측정 인프라 | 기본 Grafana 패널 | **신규**: redis-exporter 컨테이너, Tomcat thread metric 직접 등록 (`TomcatMetricsConfig`), Grafana 패널 5종 (Tomcat threads / JVM states / Prometheus scrape / Redis Health / write-app inbound) |
| 그 외 (리소스/이미지/DB 구성) | — | **stage10과 동일** |

## 호스트 사양

- **서버 호스트 (데스크탑)**: Windows 10 Pro / CPU 12 logical cores / RAM 16 GB / Docker Desktop WSL2, WSL RAM 7.7 GB / JDK ms-21.0.8

## Git commit

stage12 재측정 시점 HEAD: `4349c5b` — `refactor: Prometheus, Grafana에 패널 추가` (이전 측정은 `71915e6` 기준)
