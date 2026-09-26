#!/usr/bin/env bash
# soft TTL vs 순진한 방법(지터·락) 비교. 앱 재시작 없이 전략만 바꿔가며 연속 부하.
# 전략마다 라벨이 다르므로 한 시간창에 각각 다른 선/막대로 뜬다.
set -euo pipefail

BASE="${BASE:-http://localhost:8080}"
DURATION="${DURATION:-15s}"
STRATEGIES="${STRATEGIES:-JITTER LOCK REDIS_SOFT_TTL}"
RUN="$(date +%H%M%S)"

echo "비교 시작  KST $(date '+%H:%M:%S')   UTC $(date -u '+%H:%M:%S')"
for S in $STRATEGIES; do
  s_utc=$(date -u '+%H:%M:%S')
  k6 run -e "BASE=$BASE" -e "STRATEGY=$S" -e "KEY=ST-$RUN-$S" -e "DURATION=$DURATION" \
    k6/stampede.js 2>/dev/null | sed -n '/전략/,/재계산 비율/p'
  e_utc=$(date -u '+%H:%M:%S')
  echo "   └ $S : UTC $s_utc ~ $e_utc"
  sleep 3
done
echo "비교 끝    KST $(date '+%H:%M:%S')   UTC $(date -u '+%H:%M:%S')"
echo
echo "Grafana: 캐시 스탬피드 — 원본 재계산 (전략별 rate/누적을 위 UTC 창으로 보면 됨)"
