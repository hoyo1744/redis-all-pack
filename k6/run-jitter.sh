#!/usr/bin/env bash
# TTL 지터 OFF vs ON 을 한 번에 비교한다.
#
#   ./k6/run-jitter.sh
#
# 앱을 지터 OFF(ratio=0) → ON(ratio=0.1) 으로 각각 재시작하며 같은 k6 부하를 걸고,
# DB 재계산 총량을 나란히 찍는다. 지터는 여러 키 동시 만료에서만 효과가 있으므로
# k6/jitter.js 가 매 라운드 새 키 묶음을 써 동기화 만료를 반복 유발한다.
set -euo pipefail

BASE="${BASE:-http://localhost:8080}"
OUT="${OUT:-/tmp/jitter}"; mkdir -p "$OUT"
GRADLE="./gradlew :apps:redis-all-pack:bootRun"

start_app() {  # $1 = jitter-ratio
  pkill -f 'bootRun' 2>/dev/null || true; sleep 3
  nohup $GRADLE --args="--hotkey.stampede.jitter-ratio=$1" > logs/app-jitter.log 2>&1 &
  disown
  for _ in $(seq 1 50); do
    curl -s -m2 "$BASE/api/v1/coupons/settings" 2>/dev/null | grep -q JITTER && return 0
    sleep 5
  done
  echo "앱 기동 실패"; exit 1
}

run_phase() {  # $1 = label, $2 = ratio
  echo "── 앱 재시작: 지터 $1 (ratio=$2) ──" >&2
  start_app "$2"
  total=0
  for round in 1 2 3 4 5; do
    out=$(k6 run -e "BASE=$BASE" k6/jitter-burst.js 2>&1)
    n=$(echo "$out" | sed -n 's/.*버스트 DB 재계산 *\([0-9]*\).*/\1/p')
    echo "  ($1) round $round: ${n:-0}"
    total=$(( total + ${n:-0} ))
    sleep 2
  done
  echo "▶ 지터 $1 총 버스트 재계산: $total" | tee -a "$OUT/jitter-$1.txt"
  echo
}

echo "지터 비교 시작  $(date '+%H:%M:%S')"
run_phase OFF 0
run_phase ON  0.1
echo "결과: $OUT/jitter-OFF.txt · $OUT/jitter-ON.txt"
echo "Grafana: 캐시 스탬피드 — 원본 재계산 (JITTER rate 봉우리 비교)"
