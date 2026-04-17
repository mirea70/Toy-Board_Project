# stage0-baseline — 환경 기록

> 부하를 걸기 직전에 이 파일을 한 번 갱신하세요.
> `<TODO>` 표시된 항목은 직접 채워야 합니다.

## 실험 메타

| 항목 | 값                                 |
|---|-----------------------------------|
| 실험 ID | `stage0-baseline`                 |
| 실험 목적 | 모놀리틱 단일 인스턴스의 성능 기준선(baseline) 수립 |
| 날짜/시간 | `<2026-04-17 23:24 KST>`          |

## 아키텍처

```
[k6 container] ──▶ [nginx:80] ──▶ [app × 1 replica, cpus=0.5, mem=512M]
                                        │
                                        ├──▶ [mysql:8.0.38]
                                        └──▶ [redis:7.4]
```

- **레플리카 수**: 1 (모놀리틱 단일 인스턴스)
- **LB**: Nginx 경유 (단일 업스트림이지만 후속 실험과 경로를 동일하게 맞춤)
- **DB**: 컨테이너 MySQL, 볼륨 `mysql-data`
- **캐시**: 컨테이너 Redis (휘발성)

## 리소스 제한

| 서비스 | cpus | memory |
|---|---|---|
| app | 0.5 | 512M |
| mysql | (제한 없음) | (제한 없음) |
| redis | (제한 없음) | (제한 없음) |
| nginx | (제한 없음) | (제한 없음) |

## 이미지/버전

| 컴포넌트 | 이미지 |
|---|---|
| Java 런타임 | `eclipse-temurin:21-jdk` |
| App | `toy-board:local` (로컬 빌드) |
| MySQL | `mysql:8.0.38` |
| Redis | `redis:7.4` |
| Nginx | `nginx:1.27-alpine` |
| Prometheus | `prom/prometheus:v2.55.1` |
| Grafana | `grafana/grafana:11.3.0` |
| k6 | `grafana/k6:0.54.0` |

## 부하 시나리오

- 스크립트: [`../../k6/board-load.js`](../../k6/board-load.js)
- 트래픽 믹스 근거: [`../../k6/METHODOLOGY.md`](../../k6/METHODOLOGY.md) (NN/g 90-9-1)
- stages: `30s → 20 VU` / `1m → 50 VU` / `2m (hold 50)` / `30s → 0 VU`
- 총 소요: 약 4분

## 호스트 사양

| 항목 | 값 |
|---|---|
| OS | Windows 10 Pro 10.0.19045 |
| CPU | 12th Gen Intel Core i5-12400F (12 논리코어) |
| RAM | 16 GB (8 GB × 2) |
| 실행 환경 | Docker Desktop for Windows |
| Docker Desktop 리소스 할당 | 12 CPUs / 7.7 GB Memory |

## 사전 상태 확인

아래 명령의 출력을 복사해 둡니다 (이후 재현 시 체크용):

```bash
docker compose ps
NAME                   IMAGE                     COMMAND                   SERVICE      CREATED          STATUS                             PORTS
toy-board-app-1        toy-board:local           "java -jar /app/app.…"   app          30 seconds ago   Up 23 seconds (health: starting)   8080/tcp
toy-board-grafana      grafana/grafana:11.3.0    "/run.sh"                 grafana      30 seconds ago   Up 28 seconds                      0.0.0.0:3000->3000/tcp, [::]:3000->3000/tcp
toy-board-mysql        mysql:8.0.38              "docker-entrypoint.s…"   mysql        30 seconds ago   Up 29 seconds (healthy)            0.0.0.0:3306->3306/tcp, [::]:3306->3306/tcp
toy-board-nginx        nginx:1.27-alpine         "/docker-entrypoint.…"   nginx        29 seconds ago   Up 22 seconds (health: starting)   0.0.0.0:8080->80/tcp, [::]:8080->80/tcp
toy-board-prometheus   prom/prometheus:v2.55.1   "/bin/prometheus --c…"   prometheus   30 seconds ago   Up 29 seconds                      0.0.0.0:9090->9090/tcp, [::]:9090->9090/tcp
toy-board-redis        redis:7.4                 "docker-entrypoint.s…"   redis        30 seconds ago   Up 29 seconds (healthy)            0.0.0.0:6379->6379/tcp, [::]:6379->6379/tc

curl -s http://localhost:9090/api/v1/targets | python -c "
  import sys, json
  data = json.load(sys.stdin)
  for t in data['data']['activeTargets']:
      print(json.dumps({'job': t['labels']['job'], 'health': t['health']}))
  "
# {"job": "prometheus", "health": "up"}
# {"job": "toy-board", "health": "up"}
```

## 주의사항 / 변경점

<!-- 이전 실험 대비 바뀐 점, 특이 환경 요인 기록.
     첫 실험(baseline) 이므로 대부분 비어있어도 OK. -->

- (첫 실험 — 기준선)
