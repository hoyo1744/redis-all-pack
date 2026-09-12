#!/usr/bin/env bash
# 핫키 완화 전략 세 가지를 같은 조건에서 잰다.
#
#   ./k6/run-hotkey.sh
#   VUS=200 DURATION=60s ./k6/run-hotkey.sh
#
# **앱을 다시 띄울 필요가 없다.** 전략이 `?hot=` 쿼리 파라미터로 갈리기 때문에
# JIT 예열과 커넥션 풀 상태가 같은 조건으로 유지된다.
#
# 이 실험의 핵심 지표는 k6 출력이 아니라 **Redis 노드별 명령 수**다.
# 앱 입장에서 조회는 그냥 조회이고 어느 노드로 갈지는 Lettuce 가 정하므로,
# 앱 지표만 보면 쏠림이 보이지 않는다. 그래서 노드마다 직접 스냅샷을 뜬다.
#
# 수량을 100 만으로 두어 **절대 소진되지 않게** 한다. 소진되면 부하가 중간에 끊긴다.
set -euo pipefail

BASE="${BASE:-http://localhost:8080}"
OUT="${OUT:-/tmp/hotkey}"
DURATION="${DURATION:-30s}"
VUS="${VUS:-100}"
HOT_RATIO="${HOT_RATIO:-1.0}"
CONTAINER="${CONTAINER:-redis-all-pack-cluster-local}"
NODES="${NODES:-7001 7002 7003}"
# 잴 전략. 하나만 보려면 STRATEGIES=NONE 처럼 넘긴다.
# LOCAL_CACHE vs CAFFEINE 은 "원본 조회" 수로 갈린다 — 스탬피드 방지 효과가 거기서 보인다.
STRATEGIES="${STRATEGIES:-NONE LOCAL_CACHE CAFFEINE KEY_SHARDING}"
# ⚠️ 실행마다 달라야 한다. 이벤트 ID 를 재사용하면 등록이 조용히 실패하고
#    이전 측정의 카운터가 그대로 남아 결과가 오염된다.
RUN_ID="${RUN_ID:-$(date +%H%M%S)}"

mkdir -p "$OUT"
REPORT="$OUT/hotkey-report.txt"
: > "$REPORT"

# ── 사전 점검 ───────────────────────────────────────────────
command -v k6 >/dev/null || { echo "k6 가 없습니다. brew install k6"; exit 1; }

curl -sSf "$BASE/api/v1/coupons/settings" >/dev/null 2>&1 \
  || { echo "앱이 응답하지 않습니다: $BASE"; exit 1; }

docker exec "$CONTAINER" redis-cli -p 7001 cluster info 2>/dev/null | grep -q 'cluster_state:ok' \
  || { echo "클러스터가 정상이 아닙니다: $CONTAINER"; exit 1; }

# 노드별 누적 명령 수. 이 차이가 곧 쏠림이다.
snapshot() {
  for p in $NODES; do
    docker exec "$CONTAINER" redis-cli -p "$p" info stats 2>/dev/null \
      | awk -F: '/^total_commands_processed/{gsub(/\r/,""); print $2}'
  done | paste -sd' ' -
}

echo "핫키 측정  VUS=$VUS  DURATION=$DURATION  HOT_RATIO=$HOT_RATIO  RUN=$RUN_ID"
echo

for hot in $STRATEGIES; do
  event="EV-${RUN_ID}-${hot}"
  echo "▶ $hot" >&2

  curl -sS -XPOST "$BASE/api/v1/coupons" -H 'Content-Type: application/json' \
    -d "{\"couponId\":\"$event\",\"name\":\"리뷰이벤트\",\"totalQuantity\":1000000}" > /dev/null

  sleep 3
  before="$(snapshot)"

  k6 run --quiet --summary-export "$OUT/hotkey-${hot}.json" \
    -e "BASE=$BASE" -e "HOT=$hot" -e "COUPON=$event" \
    -e "HOT_RATIO=$HOT_RATIO" -e "VUS=$VUS" -e "DURATION=$DURATION" \
    k6/hotkey.js > "$OUT/hotkey-${hot}.txt" 2>&1 || true

  after="$(snapshot)"

  {
    echo "══════════ $hot ══════════"
    sed -n '/전략:/,/원본 조회/p' "$OUT/hotkey-${hot}.txt"
    echo "-- 노드별 명령 수 증가분 --"
    NODES="$NODES" python3 - "$before" "$after" <<'PY'
import os, sys
ports = os.environ["NODES"].split()
b = [int(x) for x in sys.argv[1].split()]
a = [int(x) for x in sys.argv[2].split()]
d = [y - x for x, y in zip(b, a)]
t = sum(d) or 1
for p, v in zip(ports, d):
    print(f"  {p} : {v:>10,}  ({v * 100 // t}%)")
print(f"  합계 : {t:,}")
print(f"  쏠림 비율 : {max(d) / t:.2f}   (0.33 균등 / 1.00 완전 쏠림)")
PY
    echo
  } | tee -a "$REPORT"

  sleep 5
done

echo "결과: $REPORT"
echo "대시보드: http://localhost:3000 → 핫키 — 슬롯 쏠림과 분배"
