# stage0-baseline 실행 가이드

> 이 파일은 첫 실험을 위한 **실행 체크리스트** 입니다. 한 번 읽고 순서대로
> 따라하세요. 다음 실험부터는 [`../README.md`](../README.md) 의 표준 절차만
> 보셔도 됩니다.

## 0. 사전 상태

- `docker-compose.yml` 의 `app.deploy.replicas: 1` 확인
- `docker-compose.yml` 의 `app.deploy.resources.limits.cpus: "0.5"` 확인
- MySQL 스키마가 `ops/mysql/init/` 에 준비되어 있음

## 1. 스택 기동

```bash
cd D:/Dev/projects/toy-board
docker compose up -d --build
docker compose ps
```

모든 서비스가 `(healthy)` 상태가 될 때까지 대기 (앱은 JIT 워밍업 포함 ~40초).

## 2. Prometheus 타겟 확인

```
http://localhost:9090/targets
```

`toy-board` job 이 `UP` 인지 확인. 여기서 DOWN 이면 Grafana 에 데이터가 없으니
실험 무효.

## 3. 환경 정보 기록

```bash
# 별도 터미널에서 실행 후 출력을 env.md 의 <TODO> 자리에 붙여넣기
git rev-parse --short HEAD
git status --short
docker compose ps
systeminfo | findstr /B /C:"OS Name" /C:"Total Physical Memory"
wmic cpu get name,NumberOfLogicalProcessors
```

## 4. 웜업 (smoke)

```bash
docker compose --profile load run --rm k6 run /scripts/smoke.js
```

JIT/커넥션 풀 워밍업. 실패율이 0.1% 미만이어야 본 실험 진행.

## 5. Grafana 준비

1. http://localhost:3000 접속 (admin / admin)
2. Dashboards → Toy Board → **Toy Board — Overview**
3. 우상단 time range: **Last 15 minutes**
4. refresh: **5s**
5. 측정 전 상태로 스크롤해둠 — 본 부하 시작 시점을 눈으로 기억

## 6. 본 부하 실행

```bash
docker compose --profile load run --rm k6 run /scripts/board-load.js --summary-export=/results/stage0-baseline/k6-summary.json |
   tee perf/results/stage0-baseline/k6-console.txt
```

> 경로 주의: `perf/results` 가 k6 컨테이너에 `/results` 로 마운트됩니다.
> 따라서 `--summary-export` 는 **컨테이너 내부 경로인 `/results/...`** 로
> 지정해야 합니다. 호스트 경로(`perf/results/...`)를 그대로 쓰면 컨테이너가
> 그 경로를 못 찾습니다.
>
> `tee` 는 k6 stdout 을 호스트의 파일로도 함께 저장하는 역할입니다. Windows
> Git Bash 에서 동작합니다.

약 4분 소요.

## 7. Grafana 스크린샷

부하 완료 직후, Grafana time range 를 **실험 4분 구간을 포함하도록** 조정
(예: `Last 5 minutes`). 아래 4장을 `stage0-baseline/` 에 저장:

| 파일명 | 어떻게 |
|---|---|
| `grafana-overview.png`  | 대시보드 전체를 한 장 |
| `grafana-latency.png`   | "Latency p50 / p95 / p99" 패널만 확대 후 한 장 |
| `grafana-hikari.png`    | "HikariCP connections" 패널만 확대 후 한 장 |
| `grafana-gc.png`        | "GC pause" 패널만 확대 후 한 장 |

Grafana 패널 우상단 `···` → `Share` → `Direct link rendered image` 로도 가능.

## 8. `notes.md` 작성

[`notes.md`](notes.md) 를 열어 `<TODO>` 자리를 채웁니다. 최소한:

- 한 줄 요약
- 한계 지표 표 (k6 콘솔 / JSON 에서 값 가져오기)
- 리소스 관찰 표 (Grafana 에서 값 가져오기)
- 병목 가설 (근거 포함)
- 놀라운 점
- 다음 실험 계획

## 9. `../README.md` 요약 표 갱신

상위 [`../README.md`](../README.md) 의 실험 결과 요약 표에서 `stage0-baseline`
행의 TBD 를 실제 수치로 교체.

## 10. 커밋

```bash
git add perf/results/stage0-baseline/
git commit -m "perf: record stage0-baseline results"
```

> 스크린샷 PNG 는 커밋해도 되지만 큰 용량이면 `git lfs` 나 별도 저장소를
> 고려하세요. 토이 프로젝트 규모면 그대로 커밋해도 무방합니다.

---

## 다음 실험으로 가기

```bash
# 레플리카 2개로 확장
docker compose up -d --scale app=2

# Prometheus 타겟이 1 → 2 로 늘었는지 확인
#   http://localhost:9090/targets

# 같은 부하 실행 (결과 경로만 stage1-scale-2 로 교체)
mkdir -p perf/results/stage1-scale-2
cp perf/results/TEMPLATE.md perf/results/stage1-scale-2/notes.md
# env.md 도 stage0 것 복사해서 변경점만 고치기
```

이 흐름이 이후 모든 실험의 표준이 됩니다.
