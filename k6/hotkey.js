// 핫키 부하. 4주차에서 한 슬롯으로 트래픽이 쏠릴 때 노드 지표가 어떻게 갈리는지 본다.
//   k6 run k6/hotkey.js
//   k6 run -e HOT_RATIO=0.5 k6/hotkey.js
import http from 'k6/http';

export const options = { vus: 100, duration: '60s' };

const BASE = __ENV.BASE || 'http://localhost:8080';
const HOT_RATIO = Number(__ENV.HOT_RATIO || 0.9);

export default function () {
  const hot = Math.random() < HOT_RATIO;
  const id = hot ? 'WELCOME' : `COUPON-${Math.floor(Math.random() * 10000)}`;
  http.get(`${BASE}/api/v1/coupons/${id}/remaining`);
}
