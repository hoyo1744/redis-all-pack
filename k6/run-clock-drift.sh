#!/usr/bin/env bash
# 클럭 드리프트로 RedLock 의 상호 배제가 깨지는 지점을 찾는다.
#
#   ./k6/run-clock-drift.sh
#
# **시계를 실제로 돌리지 않는다.** `lease` 도중 시계가 Δ 만큼 점프하는 것과
# 그 노드의 TTL 이 Δ 만큼 짧은 것은 관측상 동일하다:
#
#     점프 Δ 후 실제 만료시각 = T + lease − Δ
#     TTL 을 Δ 줄였을 때      = T + lease − Δ      ← 같다
#
# ⚠️ 3 노드 중 **2 개에만** 스큐를 준다. 전부 주면 그냥 lease 가 짧아진 것이고,
#    과반이 재구성되는 드리프트 시나리오가 아니다.
#
# ⚠️ 스큐만으로는 안 깨진다. 스큐 노드의 실효 TTL 이 **내 임계 구역보다 짧아져야**
#    남이 끼어들 틈이 생긴다. 그래서 작업 시간(pause)을 고정하고 스큐만 늘린다 —
#    그래야 "드리프트가 원인" 이라는 게 분리된다.
#
# 앱을 스큐마다 다시 띄워야 한다(@ConfigurationProperties 라 기동 시 고정).
set -euo pipefail

BASE="${BASE:-http://localhost:8080}"
OUT="${OUT:-/tmp/clock-drift}"
LEASE="${LEASE:-1000}"      # 락 수명
PAUSE="${PAUSE:-400}"       # 임계 구역 작업 시간 상한 (0~PAUSE 무작위)
VUS="${VUS:-40}"
DURATION="${DURATION:-20s}"
SKEWS="${SKEWS:-0 300 600 800 900 950}"
RUN_ID="${RUN_ID:-$$}"

mkdir -p "$OUT"; : > "$OUT/summary.psv"

for skew in $SKEWS; do
  echo "▶ 스큐 ${skew}ms (실효 TTL $((LEASE - skew))ms / 작업 최대 ${PAUSE}ms)" >&2

  pkill -f "RedisAllPackApplication" 2>/dev/null || true
  pkill -f "redis-all-pack.*bootRun" 2>/dev/null || true
  sleep 3

  # 노드 0 은 정상, 1·2 만 시계가 앞선다 → 과반이 조기 만료된다
  COUPON_LOCK_LEASE_MILLIS="$LEASE" COUPON_LOCK_WAIT_MILLIS=2000 \
  REDIS_REDLOCK_CLOCKSKEWMILLIS="0,$skew,$skew" \
    nohup ./gradlew :apps:redis-all-pack:bootRun --console=plain > "$OUT/boot-$skew.log" 2>&1 &

  until curl -sS -m 2 "$BASE/api/v1/coupons/settings" >/dev/null 2>&1; do sleep 2; done

  c="CD${RUN_ID}-$skew"
  curl -sS -XPOST "$BASE/api/v1/coupons" -H 'Content-Type: application/json' \
    -d "{\"couponId\":\"$c\",\"name\":\"드리프트\",\"totalQuantity\":1000000}" > /dev/null

  k6 run --quiet --summary-export "$OUT/skew-$skew.json" \
    -e "BASE=$BASE" -e "COUPON=$c" -e LOCK=REDLOCK -e WRITE=FENCED \
    -e "PAUSE=$PAUSE" -e "VUS=$VUS" -e "DURATION=$DURATION" \
    k6/coupon-issue.js > "$OUT/skew-$skew.txt" 2>&1 || true

  ok=$(sed -n 's/^발급 성공 *\([0-9]*\).*/\1/p' "$OUT/skew-$skew.txt" | head -1)
  stale=$(sed -n 's/^지각 쓰기 차단 *\([0-9]*\).*/\1/p' "$OUT/skew-$skew.txt" | head -1)
  to=$(sed -n 's/^락 타임아웃 *\([0-9]*\).*/\1/p' "$OUT/skew-$skew.txt" | head -1)
  echo "$skew|$((LEASE - skew))|${ok:-0}|${stale:-0}|${to:-0}" >> "$OUT/summary.psv"
done

echo
printf '%8s %12s %10s %12s %12s\n' '스큐(ms)' '실효TTL(ms)' '발급' '지각차단' '락타임아웃'
while IFS='|' read -r a b c d e; do printf '%8s %12s %10s %12s %12s\n' "$a" "$b" "$c" "$d" "$e"; done < "$OUT/summary.psv"
echo
echo "지각 차단이 0 에서 벗어나는 지점이 상호 배제가 깨지기 시작한 곳이다."
