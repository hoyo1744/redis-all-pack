// 선착순 쿠폰 발급 부하. 1~3주차 락 실험의 기본 시나리오.
//
//   k6 run k6/coupon-issue.js
//
// 먼저 쿠폰을 만들어 둔다:
//   curl -XPOST localhost:8080/api/v1/coupons \
//     -H 'Content-Type: application/json' \
//     -d '{"couponId":"WELCOME","name":"신규가입","totalQuantity":100}'
//
// 락이 없으면 issuedQuantity 가 totalQuantity 를 넘는다. 그게 1주차의 출발점이다.
import http from 'k6/http';
import { check } from 'k6';

export const options = {
  scenarios: {
    rush: { executor: 'constant-vus', vus: 100, duration: '20s' },
  },
};

const BASE = __ENV.BASE || 'http://localhost:8080';
const COUPON = __ENV.COUPON || 'WELCOME';

export default function () {
  const res = http.post(
    `${BASE}/api/v1/coupons/${COUPON}/issue`,
    JSON.stringify({ userId: `user-${__VU}-${__ITER}` }),
    { headers: { 'Content-Type': 'application/json' } },
  );

  check(res, {
    'issued': (r) => r.status === 200,
    'sold out': (r) => r.status >= 400,
  });
}
