// 선착순 쿠폰 발급 부하. 1~3주차 락 실험의 기본 시나리오.
//
//   k6 run -e LOCK=NONE     k6/coupon-issue.js
//   k6 run -e LOCK=SPIN     k6/coupon-issue.js
//   k6 run -e LOCK=REDISSON k6/coupon-issue.js
//   k6 run -e LOCK=REDLOCK  k6/coupon-issue.js
//   k6 run -e LOCK=REDISSON -e FENCED=true k6/coupon-issue.js
//
// 매 실행 전에 쿠폰을 새로 만든다. **안 하면 앞 실행이 다 써버려 다음 락은 소진만 본다.**
//   curl -XPOST localhost:8080/api/v1/coupons \
//     -H 'Content-Type: application/json' \
//     -d '{"couponId":"WELCOME","name":"신규가입","totalQuantity":100}'
//
// 끝나면 실제로 몇 장 나갔는지 확인한다. **락의 성패는 여기서 갈린다.**
//   curl localhost:8080/api/v1/coupons/WELCOME/remaining
//
// LOCK=NONE 이면 issuedQuantity 가 totalQuantity 를 넘는다. 그게 1주차의 출발점이다.
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

export const options = {
  scenarios: {
    rush: {
      executor: 'constant-vus',
      vus: Number(__ENV.VUS || 100),
      duration: __ENV.DURATION || '20s',
    },
  },
  // 락별로 p95 를 나란히 놓고 본다. 스핀은 대기 중에도 왕복을 써서 여기가 벌어진다.
  thresholds: {
    'http_req_duration{expected_response:true}': ['p(95)<1000'],
  },
};

const BASE = __ENV.BASE || 'http://localhost:8080';
const COUPON = __ENV.COUPON || 'WELCOME';
const LOCK = __ENV.LOCK || 'NONE';
const WRITE = __ENV.WRITE || 'PLAIN';
// 임계 구역에서 멈출 시간. lease 보다 크게 주면 락이 만료된 채 쓰기가 나간다 (GC STW 흉내).
const PAUSE = __ENV.PAUSE || '0';

// 실패를 종류별로 센다. 뭉치면 "락을 못 잡은 것" 과 "소진된 것" 이 한 덩어리가 돼
// 락의 성능과 정확성을 구분할 수 없다.
const issued = new Counter('coupon_issued');
const soldOut = new Counter('coupon_sold_out');
const lockTimeout = new Counter('coupon_lock_timeout');
const staleFence = new Counter('coupon_stale_fence');

export default function () {
  const url = `${BASE}/api/v1/coupons/${COUPON}/issue?lock=${LOCK}&write=${WRITE}&pause=${PAUSE}`;

  const res = http.post(
    url,
    JSON.stringify({ userId: `user-${__VU}-${__ITER}` }),
    { headers: { 'Content-Type': 'application/json' } },
  );

  if (res.status === 200) {
    issued.add(1);
  } else if (res.status === 429) {
    lockTimeout.add(1);
  } else if (res.status === 409) {
    // 응답 본문의 code 로 소진과 지각 쓰기를 가른다.
    // staleFence 가 0 이 아니면 **락이 뚫렸다는 증거다.**
    const code = res.json('code');
    if (code === 'STALE_FENCE') staleFence.add(1);
    else soldOut.add(1);
  }

  check(res, {
    'issued or rejected (not 5xx)': (r) => r.status < 500,
  });
}

export function handleSummary(data) {
  const get = (name) => (data.metrics[name] ? data.metrics[name].values.count : 0);

  const lines = [
    '',
    `락: ${LOCK}   쓰기: ${WRITE}   정지: ${PAUSE}ms`,
    '─'.repeat(40),
    `발급 성공      ${get('coupon_issued')}`,
    `소진           ${get('coupon_sold_out')}`,
    `락 타임아웃    ${get('coupon_lock_timeout')}`,
    `지각 쓰기 차단 ${get('coupon_stale_fence')}   ← 0 이 아니면 락이 뚫렸다`,
    '',
    '실제 발급 수량을 반드시 확인할 것:',
    `  curl ${BASE}/api/v1/coupons/${COUPON}/remaining`,
    '',
  ];

  return { stdout: lines.join('\n') };
}
