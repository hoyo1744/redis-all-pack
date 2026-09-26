// 캐시 스탬피드 — soft TTL vs 순진한 방법(지터·락) 비교.
//
// 하나의 핫키에 연속 부하를 건다. 짧은 TTL(1초)이라 매초 만료되고, 그 순간 방어 전략마다
// DB 재계산이 얼마나 몰리는지 본다.
//   JITTER          : 무방비 — 만료마다 떼몰림 (단일 키라 지터 효과 없음)
//   LOCK            : SET NX 로 하나만 재계산 (대신 못 잡은 요청은 대기)
//   REDIS_SOFT_TTL  : 낡은 값 즉시 + 하나만 백그라운드 갱신
//
//   앱 재시작 없이 전략만 바꿔가며:
//     STRATEGY=JITTER          k6 run k6/stampede.js
//     STRATEGY=LOCK            k6 run k6/stampede.js
//     STRATEGY=REDIS_SOFT_TTL  k6 run k6/stampede.js
//   또는 한 번에:  ./k6/run-stampede.sh
//
// 측정: 응답 `fromCache=false` = DB 재계산. 서버 지표
// cache_origin_recompute_total{strategy} 와 같은 신호를 클라이언트에서도 센다.
import http from 'k6/http';
import { Counter, Rate } from 'k6/metrics';

const BASE = __ENV.BASE || 'http://localhost:8080';
const STRATEGY = __ENV.STRATEGY || 'JITTER';
const KEY = __ENV.KEY || `STAMP-${STRATEGY}`;

export const options = {
  scenarios: {
    hot: {
      executor: 'constant-vus',
      vus: Number(__ENV.VUS || 20),
      duration: __ENV.DURATION || '15s',
    },
  },
};

const recompute = new Counter('stampede_recompute');
const recomputeRate = new Rate('stampede_recompute_ratio');

export function setup() {
  http.post(`${BASE}/api/v1/coupons`, JSON.stringify({
    couponId: KEY, name: 'stampede', totalQuantity: 1000,
  }), { headers: { 'Content-Type': 'application/json' } });
}

export default function () {
  const res = http.get(`${BASE}/api/v1/coupons/${KEY}/remaining?hot=${STRATEGY}`);
  if (res.status === 200) {
    const miss = res.json('fromCache') === false;
    recompute.add(miss ? 1 : 0);
    recomputeRate.add(miss);
  }
}

export function handleSummary(data) {
  const c = (n) => (data.metrics[n] ? data.metrics[n].values.count : 0);
  const r = (n) => (data.metrics[n] ? data.metrics[n].values.rate : 0);
  const reqs = data.metrics.http_reqs ? data.metrics.http_reqs.values : {};
  const dur = data.metrics.http_req_duration ? data.metrics.http_req_duration.values : {};
  return {
    stdout: [
      '',
      `전략          ${STRATEGY}`,
      `총 요청       ${reqs.count || 0}  (${(reqs.rate || 0).toFixed(0)} rps)`,
      `DB 재계산     ${c('stampede_recompute')}   ← 무방비일수록 크다`,
      `재계산 비율   ${(r('stampede_recompute_ratio') * 100).toFixed(2)}%`,
      `p95           ${(dur['p(95)'] || 0).toFixed(1)}ms`,
      '',
    ].join('\n'),
  };
}
