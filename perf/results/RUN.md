# 실험 실행 가이드 (공통)

모든 stage 에 공통으로 적용되는 **실행 체크리스트**. stage 별로 다른 값
(`cpus`, `replicas`, VU 등)은 해당 stage 의 `env.md` 에 기록되며,
이 문서에서는 `<STAGE_ID>` 플레이스홀더를 사용합니다.

> 실험 간 요약표와 폴더 구조는 [`README.md`](README.md) 먼저 확인.

## 0. 사전 준비

### 0-1. stage 폴더 생성

**Git Bash:**

```bash
export STAGE_ID=stageN           # 예: stage14
export PREV_STAGE=stageN-1       # 예: stage13

mkdir -p perf/results/$STAGE_ID
cp perf/results/$PREV_STAGE/notes.md perf/results/$STAGE_ID/notes.md
cp perf/results/$PREV_STAGE/env.md perf/results/$STAGE_ID/env.md
```

**PowerShell:**

```powershell
$env:STAGE_ID   = "stageN"
$env:PREV_STAGE = "stageN-1"

New-Item -ItemType Directory -Force -Path "perf/results/$env:STAGE_ID" | Out-Null
Copy-Item "perf/results/TEMPLATE.md"             "perf/results/$env:STAGE_ID/notes.md"
Copy-Item "perf/results/$env:PREV_STAGE/env.md"  "perf/results/$env:STAGE_ID/env.md"
```

> `env.md` 는 이전 stage 것을 복사해서 **diff 만 수정**. 아키텍처가 크게 바뀌는
> 경우(예: MSA 전환) 처음부터 새로 작성.
>
> **PowerShell 주의**: `$env:STAGE_ID` 는 환경변수라 이후 `docker compose ...`
> 에도 보간되지만, `$STAGE_ID` (지역변수) 만 설정하면 컨테이너에 전달되지 않음.

### 0-2. stage 특정 구성 반영

해당 stage 에서 바꿀 조건(`cpus` / `replicas` / VU 등)은 `docker-compose.yml` 과
`perf/k6/board-load.js` 에 먼저 반영. 이후 `env.md` 에서:

1. 헤더(`# stageN — 환경 기록`) + 실험 ID / 목적 / 날짜 수정
2. 리소스 제한 / 부하 시나리오(`stages`) 값 갱신
3. **"이전 stage 대비 변경점"** 표 교체
4. 변경 없는 섹션(호스트 사양, 이미지 버전 등)은 `"stageX 와 동일 — 참조"` 로 축약

### 0-3. 체크

- `docker-compose.yml` 의 `write-app` / `article-read` / `sub-read` 각 `replicas` / `resources.limits` 가 의도한 값인지
- MySQL 스키마(비즈니스 테이블 + `outbox` 테이블)가 `ops/mysql/init/` 에 준비돼 있는지
- Kafka 가 healthy (`docker compose ps` 에서 `toy-board-kafka`)
- 이전 실험 잔여물 없는지 — 필요시 `docker compose down -v --remove-orphans`

## 1. 스택 기동

```bash
docker compose up -d --build
docker compose ps
```

모든 서비스가 `(healthy)` 될 때까지 대기 (앱은 JIT 워밍업 포함 ~40초).

## 2. Prometheus 타겟 확인

`http://localhost:9090/targets` — `toy-board-write` / `toy-board-article-read` /
`toy-board-sub-read` job 이 모두 `UP` 이어야 유효. (stage13 부터 read 가
article-read / sub-read 두 서비스로 분리됨.) DOWN 이면 Grafana 에 데이터가
없으니 실험 무효.

## 3. 환경 정보 기록 (`env.md`)

```bash
# 아래 출력을 env.md 해당 자리에 붙여넣기
git rev-parse --short HEAD
git status --short
docker compose ps
systeminfo | findstr /B /C:"OS Name" /C:"Total Physical Memory"
wmic cpu get name,NumberOfLogicalProcessors
```

최소 기록 항목: 실험 ID / 날짜 / 아키텍처 / `docker compose ps` / CPU·메모리
제한 / 이미지 버전 / **git commit 해시** / 호스트 사양 / **이전 stage 대비 변경점**.

## 4. 웜업 (smoke)

```bash
docker compose --profile load run --rm k6 run //scripts/smoke.js
```

JIT / 커넥션 풀 워밍업. 실패율 0.1% 미만이어야 본 실험 진행.

## 5. Grafana 준비

1. `http://localhost:3000` 접속 (admin / admin)
2. Dashboards → Toy Board → **Toy Board — Overview**
3. 우상단 time range **Last 15 minutes**, refresh **5s**
4. 측정 전 상태로 스크롤해 부하 시작 시점을 눈으로 기억

> 패널들은 Legend `table` 모드라 구간 **평균 / 최대 / 끝값** 이 스크린샷에
> 자동 포함됩니다. 별도로 숫자를 읽을 필요 없음.

## 6. 본 부하 실행

### Git Bash

```bash
# 세션 한 번(또는 ~/.bashrc)
export MSYS_NO_PATHCONV=1

docker compose --profile load run --rm k6 run /scripts/board-load.js \
  --summary-export=/results/$STAGE_ID/k6-summary.json \
  | tee perf/results/$STAGE_ID/k6-console.txt
```

`MSYS_NO_PATHCONV` 를 안 쓸 경우 컨테이너 내부 경로를 모두 `//` 로 시작해야 함.
그냥 `/scripts/...`, `/results/...` 를 쓰면 Git 설치 폴더 기준으로 경로가
변환되어 **k6 가 스크립트를 못 찾거나 `--summary-export` 저장에 실패** (stage3
에서 실제 발생):

```bash
docker compose --profile load run --rm k6 run //scripts/board-load.js \
  --summary-export=//results/$STAGE_ID/k6-summary.json \
  | tee perf/results/$STAGE_ID/k6-console.txt
```

### PowerShell

```powershell
# 한 줄로 붙이거나 백틱(`) 줄바꿈. $env:STAGE_ID 보간 확인.
docker compose --profile load run --rm k6 run /scripts/board-load.js --summary-export=/results/$env:STAGE_ID/k6-summary.json | Tee-Object -FilePath perf/results/$env:STAGE_ID/k6-console.txt
```

> - `/results/...` 는 **k6 컨테이너 내부 경로** (호스트 `perf/results` 가 마운트됨). 호스트 경로를 쓰면 컨테이너가 못 찾음.
> - `tee` 는 k6 stdout 을 호스트 파일로도 저장 — Git Bash `tee`, PowerShell `Tee-Object`.
> - 대상 디렉토리는 0-1 에서 이미 생성했습니다.

**소요 시간:**

| 시나리오 | 시간 |
|---|---|
| 압축 (stage0~2) | 약 4분 |
| 현실 v1, hold 11분 (stage3~8) | 약 17~18분 |
| **현실 v2, hold 4분 (stage9+)** ← 현재 | 약 7.5분 |

방법론 전환 근거: [`METHODOLOGY.md §4`](../k6/METHODOLOGY.md).

## 7. Grafana 스크린샷

부하 완료 직후, time range 를 조정해 **첫 램프 구간(워밍업)은 제외** 하고
hold 구간만 포함:

| 시나리오 | 제외 구간 | 스크린샷 time range |
|---|---|---|
| 압축 (stage0~2) | 첫 30s | 부하 시작 +30s ~ 종료 |
| 현실 v1 (stage3~8) | 첫 3분 | +3m ~ 종료 |
| **현실 v2 (stage9+)** | 첫 3분 | +3m ~ 종료 (hold 4분 캡처) |

Grafana 우상단 time picker → "Absolute time range" 로 정확히 지정.

> **왜 램프 구간을 버리나?** JIT / 캐시 / HTTP keep-alive 가 데워지는 **자연
> 워밍업 구간**. cold-start 스파이크가 섞이면 리소스 평균/최대값이 왜곡됩니다.
> k6 summary JSON 은 전체 run 을 포함하므로 **threshold 통과 여부 기준으로만**
> 쓰고, **Grafana 수치는 반드시 램프 이후로 잘라서** 보세요.

`perf/results/$STAGE_ID/` 에 저장할 PNG:

**필수 4종**

| 파일명 | 내용 |
|---|---|
| `grafana-overview.png` | 대시보드 전체 (latency / hikari / gc 추세는 여기서 확인) |
| `grafana-latency.png` | p50 / p95 / p99 패널 단독 (가장 자주 인용) |
| `grafana-cpu.png` | Spring CPU Usage by Instance (서비스별 부하 분담) |
| `grafana-timeshare.png` | Server time share by method + URI (URI 별 시간 점유) |

**상황 따라**

| 파일명                                 | 언제                                   |
|-------------------------------------|--------------------------------------|
| `grafana-endpoint.png`              | 엔드포인트별 누적 req / CPU time / 건당 평균 CPU |
| `grafana-endpoint-latency95.png`    | 엔드포인트별 95% 수준 latency                |
| `grafana-request-rate.png`          | 엔드포인트별 처리량 (Req/s)                   |
| `grafana-kafka-lag.png`             | MSA (stage9+) consumer lag 추적        |
| `grafana-tomcat-thread.png`         | Tomcat busy / queuing 감지             |
| `grafana-jvm-thread.png`            | JVM 스레드 상태 체크                        |
| `grafana-prometheus-scrape.png`     | 스크래핑 실패 여부 체크                        |
| `grafana-redis-health.png`          | Redis 연결 상태 감지                       |
| `grafana-write-app-concurrency.png` | write 앱 동시 요청 감지                     |

> hikari / gc 등 detail 패널은 overview 안의 Legend 숫자로 충분. 단독 캡처는
> `notes.md` 에서 핵심 수치 보충할 때만 추가.

패널 우상단 `···` → `Share` → `Direct link rendered image` 로도 저장 가능.

## 8. 관찰 노트 (`notes.md`) 작성

[`TEMPLATE.md`](TEMPLATE.md) 양식에 채웁니다. 핵심:

- **한 줄 요약** (결과 + 병목 한 문장)
- **한계 지표** 표 (k6 콘솔 / JSON)
- **리소스 사용** 표 (Grafana Legend 숫자 그대로)
- **병목 가설** (시그널 → 근거 → 결론)
- 놀라운 점 / 다음 실험 계획 (선택)

## 9. 상위 요약표 갱신 (`README.md`)

[`README.md`](README.md) 요약 표의 해당 stage 행 TBD 를 실제 수치로 교체.
새 stage 면 행 추가.

## 10. 커밋

```bash
git add perf/results/$STAGE_ID/
git commit -m "perf: record $STAGE_ID results"
```

> 스크린샷 PNG 는 용량이 크면 `git lfs` 고려. 토이 프로젝트 규모면 그대로 커밋 무방.

---

## 재현성 체크리스트

실험 시작 전에:

- [ ] `docker compose ps` — 모든 서비스가 `(healthy)`
- [ ] `git status` clean (또는 변경점을 `env.md` 에 기록)
- [ ] Prometheus targets — `toy-board-*` 모두 UP
- [ ] Grafana 접근 가능, 이전 실험 잔여 데이터 없는지
- [ ] `board-load.js` / `METHODOLOGY.md` 가 이전 실험과 동일한지 (바뀌면 비교 무효)
- [ ] 호스트에서 다른 CPU 먹는 작업 없는지 (브라우저 영상, 빌드 등 종료)

---

## 다음 실험으로 가기

```bash
# 조건 변경 예시
docker compose up -d --scale article-read=2   # 읽기(article) 인스턴스 확장
docker compose up -d --scale sub-read=2       # 읽기(sub) 인스턴스 확장
docker compose up -d --scale write-app=2      # 쓰기 인스턴스 확장
# 또는 docker-compose.yml 의 cpus / memory 값 수정 후 재기동

# Prometheus 타겟이 의도대로 변했는지 확인
#   http://localhost:9090/targets

# 0-1 부터 다시 (STAGE_ID 만 바꿔서)
```
