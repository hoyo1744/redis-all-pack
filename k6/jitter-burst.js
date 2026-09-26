// TTL 지터 ON vs OFF — 버스트 버전. 지터가 실제로 효과를 내는 조건을 연출한다.
//
// 지터는 "여러 키가 동시에 만료 + 그 순간 몰림"이라는 **전이 구간**에서만 일한다.
// 연속 부하(k6/jitter.js)에선 키가 자연 desync 되어 차이가 거의 없다. 그래서 두 시나리오를
// 시차를 두고 배치한다:
//   1) warm  (t=0)        : KEYS 개 키를 동시에 한 번씩 읽어 캐시를 채운다 → TTL 이 정렬된다
//   2) burst (t=BURST_AT) : 만료 순간에 스파이크로 몰아친다
//     - 지터 OFF: t=1000ms 에 KEYS 개가 다 만료 → 스파이크가 전부 미스 → 떼몰림
//     - 지터 ON : 만료가 1000~1100ms 로 퍼짐 → 스파이크 시점(1050ms)엔 절반만 만료 → 절반만 미스
//
//   앱을 지터 OFF/ON 으로 각각 띄우고:  k6 run k6/jitter-burst.js
//   또는 한 번에:  ./k6/run-jitter.sh
//
// 측정: burst 시나리오의 `fromCache=false`(=DB 재계산) 총합. OFF 가 크면 지터가 일한 것이다.
import http from 'k6/http';
import { Counter } from 'k6/metrics';

const BASE = __ENV.BASE || 'http://localhost:8080';
const KEYS = Number(__ENV.KEYS || 200);
const BURST_AT = __ENV.BURST_AT || '1050ms';   // 만료(1000ms) 직후, 지터 밴드(1000~1100) 중간
const BURST = Number(__ENV.BURST || 400);      // 만료 순간 한 파도로 쏠 동시 요청 수

export const options = {
  scenarios: {
    warm: {
      executor: 'shared-iterations',
      exec: 'warm',
      vus: KEYS,
      iterations: KEYS,
      startTime: '0s',
      maxDuration: '2s',
    },
    burst: {
      // 각 VU 가 딱 한 발씩 → 만료 순간의 '순간 버스트'. constant-vus(최소 1s)로는
      // 1초 연속 부하가 되어 키가 desync 되므로 shared-iterations 로 한 파도만 만든다.
      executor: 'shared-iterations',
      exec: 'burst',
      vus: BURST,
      iterations: BURST,
      startTime: BURST_AT,
      maxDuration: '3s',
    },
  },
};

const recompute = new Counter('burst_recompute');   // burst 구간의 fromCache=false 총합

export function setup() {
  for (let i = 0; i < KEYS; i++) {
    http.post(`${BASE}/api/v1/coupons`, JSON.stringify({
      couponId: `JB-${i}`, name: 'jitter-burst', totalQuantity: 1000,
    }), { headers: { 'Content-Type': 'application/json' } });
  }
}

// t=0: 각 VU 가 자기 인덱스 키를 한 번 읽어 동시에 populate → TTL 정렬
export function warm() {
  http.get(`${BASE}/api/v1/coupons/JB-${__VU - 1}/remaining?hot=JITTER`);
}

// t=BURST_AT: 만료 순간 랜덤 키로 몰아치기
export function burst() {
  const res = http.get(`${BASE}/api/v1/coupons/JB-${Math.floor(Math.random() * KEYS)}/remaining?hot=JITTER`);
  if (res.status === 200 && res.json('fromCache') === false) recompute.add(1);
}

export function handleSummary(data) {
  const c = (n) => (data.metrics[n] ? data.metrics[n].values.count : 0);
  return {
    stdout: [
      '',
      `버스트 DB 재계산   ${c('burst_recompute')}   ← 지터 OFF 가 크면 떼몰림, ON 이면 절반`,
      `(키 ${KEYS}개 동시 만료, 버스트 @${BURST_AT})`,
      '',
    ].join('\n'),
  };
}
