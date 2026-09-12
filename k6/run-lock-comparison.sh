#!/usr/bin/env bash
# 락 · 쓰기 방식을 같은 조건에서 나란히 잰다.
#
#   ./k6/run-lock-comparison.sh            기본 (락 비교 + 쓰기 방식 비교)
#   PHASE=breach ./k6/run-lock-comparison.sh   락 뚫림 재현 (lease 짧게 띄운 앱 필요)
#
# **두 번 잰다. 재는 것이 다르기 때문이다.**
#   정확성  수량을 적게(100) 두고 소진시킨다 → 초과 발급 · 허수 응답이 나는지
#   성능    수량을 크게(1,000,000) 두어 절대 소진되지 않게 한다 → 락 경합만 반영
set -euo pipefail

BASE="${BASE:-http://localhost:8080}"
OUT="${OUT:-/tmp/lock-comparison}"
DURATION="${DURATION:-20s}"
VUS="${VUS:-100}"
PHASE="${PHASE:-main}"
# 실행마다 고유해야 한다. 같은 쿠폰 ID 를 다시 쓰면 register 가 unique 제약에 걸려
# 조용히 실패하고, **앞 실행의 발급 수량 위에 누적돼** DB 숫자가 오염된다.
RUN_ID="${RUN_ID:-$$}"

mkdir -p "$OUT"

register() { curl -sS -XPOST "$BASE/api/v1/coupons" -H 'Content-Type: application/json' \
  -d "{\"couponId\":\"$1\",\"name\":\"비교실험\",\"totalQuantity\":$2}" > /dev/null; }

remaining() { curl -sS "$BASE/api/v1/coupons/$1/remaining" | sed -n 's/.*"remaining":\([-0-9]*\).*/\1/p'; }

run() { # label lock write qty tag pause vus duration
  local label="$1" lock="$2" write="$3" qty="$4" tag="$5"
  local pause="${6:-0}" vus="${7:-$VUS}" dur="${8:-$DURATION}"
  local slug; slug="$(echo "$label" | tr ' /+' '___')"
  local coupon="C${RUN_ID}-${tag}-${slug}"

  echo "▶ $label" >&2
  register "$coupon" "$qty"

  k6 run --quiet --summary-export "$OUT/${tag}-${slug}.json" \
    -e "BASE=$BASE" -e "COUPON=$coupon" -e "LOCK=$lock" -e "WRITE=$write" -e "PAUSE=$pause" \
    -e "VUS=$vus" -e "DURATION=$dur" \
    k6/coupon-issue.js > "$OUT/${tag}-${slug}.txt" 2>&1 || true

  local rem issued
  rem="$(remaining "$coupon")"; issued=$(( qty - rem ))
  echo "$label|$qty|$issued|$rem" >> "$OUT/${tag}-summary.psv"
}

echo "=== 설정 ==="; curl -sS "$BASE/api/v1/coupons/settings"; echo; echo

if [[ "$PHASE" == "breach" ]]; then
  # ── 락 뚫림 재현 ────────────────────────────────────────────────────
  # 앱을 lease 200ms 로 띄운 상태여야 한다:
  #   COUPON_LOCK_LEASE_MILLIS=200 ./gradlew :apps:redis-all-pack:bootRun
  # 임계 구역에서 1 초 멈추므로 **락은 반드시 만료된다.**
  : > "$OUT/breach-summary.psv"
  for write in PLAIN FENCED_NAIVE FENCED; do
    run "REDISSON+$write" REDISSON "$write" 1000000 breach 1000 20 20s
  done
  echo; echo "결과: $OUT"; exit 0
fi

# ── 1) 정확성: 소진시켜 초과 발급 · 허수 응답을 본다 ────────────────────
: > "$OUT/correctness-summary.psv"
for lock in NONE SPIN REDISSON REDLOCK; do
  run "$lock" "$lock" PLAIN 100 correctness
done

# ── 2) 쓰기 방식: 락 없이 DB 만으로 얼마나 막히나 ──────────────────────
: > "$OUT/write-summary.psv"
for write in PLAIN ATOMIC FENCED; do
  run "NONE+$write" NONE "$write" 100 write
done

# ── 3) 성능: 소진되지 않게 두어 락 경합만 잰다 ─────────────────────────
: > "$OUT/perf-summary.psv"
for lock in NONE SPIN REDISSON REDLOCK; do
  run "$lock" "$lock" PLAIN 1000000 perf
done
run "NONE+ATOMIC" NONE ATOMIC 1000000 perf

echo; echo "결과: $OUT"
