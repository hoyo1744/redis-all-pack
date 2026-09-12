// 핫키 부하 — 조회가 한 슬롯에 몰릴 때 노드별 지표가 어떻게 갈리는지 본다.
//
//   k6 run -e HOT=NONE         k6/hotkey.js     완화 없음 (기준선)
//   k6 run -e HOT=LOCAL_CACHE  k6/hotkey.js     앱 메모리에서 답
//   k6 run -e HOT=KEY_SHARDING k6/hotkey.js     키를 쪼개 슬롯을 흩는다
//
// 먼저 쿠폰을 만들어 둔다:
//   curl -XPOST localhost:8080/api/v1/coupons -H 'Content-Type: application/json' \
//     -d '{"couponId":"HOT","name":"핫키","totalQuantity":1000000}'
//
// 측정 대상은 **Redis 노드별 초당 명령 수**다. 앱 지표만 보면 쏠림이 안 보인다.
//   Grafana → 분산락 대시보드 → "Redis 노드별 초당 명령 수"
import http from 'k6/http';
import { Counter } from 'k6/metrics';

const BASE = __ENV.BASE || 'http://localhost:8080';
const HOT = __ENV.HOT || 'NONE';
const COUPON = __ENV.COUPON || 'HOT';
// 핫키 비율. 1.0 이면 전부 한 쿠폰으로, 0.5 면 절반은 흩어진 쿠폰으로 간다.
const HOT_RATIO = Number(__ENV.HOT_RATIO || 1.0);
const COLD_KEYS = Number(__ENV.COLD_KEYS || 10000);

export const options = {
  scenarios: {
    read: {
      executor: 'constant-vus',
      vus: Number(__ENV.VUS || 100),
      duration: __ENV.DURATION || '30s',
    },
  },
};

const fromCache = new Counter('hotkey_from_cache');
const fromOrigin = new Counter('hotkey_from_origin');

export default function () {
  // 핫키는 항상 같은 쿠폰 = 항상 같은 슬롯 = 항상 같은 노드
  const id = Math.random() < HOT_RATIO
    ? COUPON
    : `COLD-${Math.floor(Math.random() * COLD_KEYS)}`;

  const res = http.get(`${BASE}/api/v1/coupons/${id}/remaining?hot=${HOT}`);

  if (res.status === 200) {
    // fromCache=false 는 원본(DB)까지 갔다는 뜻이다. 로컬 캐시 효과가 여기 드러난다.
    if (res.json('fromCache') === true) fromCache.add(1);
    else fromOrigin.add(1);
  }
}

export function handleSummary(data) {
  const g = (n) => (data.metrics[n] ? data.metrics[n].values.count : 0);
  const reqs = data.metrics.http_reqs ? data.metrics.http_reqs.values : {};
  const dur = data.metrics.http_req_duration ? data.metrics.http_req_duration.values : {};

  return {
    stdout: [
      '',
      `전략: ${HOT}   핫키 비율: ${HOT_RATIO}`,
      '─'.repeat(50),
      `처리량        ${(reqs.rate || 0).toFixed(1)} rps  (총 ${reqs.count || 0})`,
      `avg / p95     ${(dur.avg || 0).toFixed(1)}ms / ${(dur['p(95)'] || 0).toFixed(1)}ms`,
      `캐시 적중     ${g('hotkey_from_cache')}`,
      `원본 조회     ${g('hotkey_from_origin')}   ← 이 값이 작을수록 Redis·DB 부하가 준다`,
      '',
      'Redis 노드별 쏠림은 Grafana 에서 확인할 것:',
      '  http://localhost:3000 → 분산락 대시보드 → Redis 노드별 초당 명령 수',
      '',
    ].join('\n'),
  };
}
