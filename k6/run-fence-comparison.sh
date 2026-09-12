#!/usr/bin/env bash
# 펜싱 토큰 출처 두 가지를 같은 조건에서 잰다.
#
# **앱을 두 번 띄워야 한다.** 구현체가 @ConditionalOnProperty 로 갈리기 때문이다.
#   COUPON_FENCE_SOURCE=redis ./gradlew :apps:redis-all-pack:bootRun
#   COUPON_FENCE_SOURCE=rdb   ./gradlew :apps:redis-all-pack:bootRun
#
# 각각 띄워놓고:
#   OUT=/tmp/fence-redis ./k6/run-fence-comparison.sh
#   OUT=/tmp/fence-rdb   ./k6/run-fence-comparison.sh
#
# 수량을 100 만으로 두어 **절대 소진되지 않게** 한다. 소진이 섞이면 토큰 발급 비용이 아니라
# 소진 처리 비용을 재게 된다.
set -euo pipefail

BASE="${BASE:-http://localhost:8080}"
OUT="${OUT:-/tmp/fence-comparison}"
DURATION="${DURATION:-20s}"
VUS="${VUS:-100}"
RUN_ID="${RUN_ID:-$$}"

mkdir -p "$OUT"; : > "$OUT/fence-summary.psv"

SOURCE="$(curl -sS "$BASE/api/v1/coupons/settings" | sed -n 's/.*"fenceSource":"\([^"]*\)".*/\1/p')"
echo "토큰 출처: $SOURCE"

for lock in NONE REDISSON; do
  coupon="F${RUN_ID}-${lock}"
  echo "▶ $lock" >&2

  curl -sS -XPOST "$BASE/api/v1/coupons" -H 'Content-Type: application/json' \
    -d "{\"couponId\":\"$coupon\",\"name\":\"펜스비교\",\"totalQuantity\":1000000}" > /dev/null

  k6 run --quiet --summary-export "$OUT/fence-${lock}.json" \
    -e "BASE=$BASE" -e "COUPON=$coupon" -e "LOCK=$lock" -e "WRITE=FENCED" -e "PAUSE=0" \
    -e "VUS=$VUS" -e "DURATION=$DURATION" \
    k6/coupon-issue.js > "$OUT/fence-${lock}.txt" 2>&1 || true

  rem="$(curl -sS "$BASE/api/v1/coupons/$coupon/remaining" | sed -n 's/.*"remaining":\([-0-9]*\).*/\1/p')"
  echo "${SOURCE}/${lock}|1000000|$(( 1000000 - rem ))|$rem" >> "$OUT/fence-summary.psv"
done

echo; echo "결과: $OUT"
