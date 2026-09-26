// TTL 지터 ON vs OFF 비교 — 캐시 스탬피드.
//
//   앱을 지터 OFF/ON 으로 각각 띄우고 같은 부하를 건다:
//     ./gradlew :apps:redis-all-pack:bootRun --args='--hotkey.stampede.jitter-ratio=0'    # OFF
//     ./gradlew :apps:redis-all-pack:bootRun --args='--hotkey.stampede.jitter-ratio=0.1'  # ON
//   각각에 대해:
//     k6 run k6/jitter.js
//   또는 한 번에:  ./k6/run-jitter.sh
//
// 핵심: 지터는 **단일 키엔 무력**하다. **여러 키가 동시에 만료**될 때만 효과가 있다.
// 그래서 매 라운드(ROUND_MS)마다 **새 키 묶음**(PER_ROUND 개)을 쓴다. 그 묶음은 직전까지
// 안 쓰여 캐시가 비어 있으므로, 라운드 시작에 **동시에 populate → TTL 정렬 → 동시 만료**된다.
//   지터 OFF: 정렬된 만료 순간 떼몰림 → fromCache=false(재계산) 봉우리
//   지터 ON : 만료가 밴드로 퍼짐       → 봉우리가 뭉개짐
//
// 측정: 응답의 `fromCache=false` 가 곧 DB 재계산이다. 서버의
// cache_origin_recompute_total{strategy="JITTER"} 와 같은 신호를 클라이언트에서 센다.
import http from 'k6/http';
import { Counter, Rate } from 'k6/metrics';

const BASE = __ENV.BASE || 'http://localhost:8080';
const POOL = Number(__ENV.POOL || 300);          // 미리 등록해 둘 키 풀
const PER_ROUND = Number(__ENV.PER_ROUND || 20); // 라운드당 동시 만료시킬 키 수
const ROUND_MS = Number(__ENV.ROUND_MS || 2000); // 라운드 길이 (TTL 1초 > 여유)

export const options = {
  scenarios: {
    burst: {
      executor: 'constant-vus',
      vus: Number(__ENV.VUS || 80),
      duration: __ENV.DURATION || '30s',
    },
  },
};

const recompute = new Counter('stampede_recompute');   // fromCache=false 총합
const recomputeRate = new Rate('stampede_recompute_ratio');

// 현재 시각으로 라운드를 정하고, 그 라운드에 배정된 PER_ROUND 개 키 중 하나를 고른다.
// 라운드마다 풀에서 겹치지 않는 창을 잡아 쓰므로, 재사용 주기가 (POOL/PER_ROUND)*ROUND_MS
// = 30초로 길어 매번 '식은' 키가 된다 → 동기화 populate/만료가 반복된다.
function pickKey() {
  const round = Math.floor(Date.now() / ROUND_MS);
  const base = (round * PER_ROUND) % POOL;
  const idx = (base + Math.floor(Math.random() * PER_ROUND)) % POOL;
  return `JK-${idx}`;
}

export function setup() {
  for (let i = 0; i < POOL; i++) {
    http.post(`${BASE}/api/v1/coupons`, JSON.stringify({
      couponId: `JK-${i}`, name: 'jitter-pool', totalQuantity: 1000,
    }), { headers: { 'Content-Type': 'application/json' } });
  }
}

export default function () {
  const res = http.get(`${BASE}/api/v1/coupons/${pickKey()}/remaining?hot=JITTER`);
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
      `총 요청       ${reqs.count || 0}  (${(reqs.rate || 0).toFixed(0)} rps)`,
      `DB 재계산     ${c('stampede_recompute')}`,
      `재계산 비율   ${(r('stampede_recompute_ratio') * 100).toFixed(2)}%`,
      `p95           ${(dur['p(95)'] || 0).toFixed(1)}ms`,
      '',
      '⚠️ 연속 고부하에선 키가 자연 desync 되어 지터 OFF/ON 차이가 거의 없다.',
      '   (지터는 "동시 만료 + 그 순간 버스트" 라는 전이 구간에서만 효과가 있다.)',
      '   전이 봉우리는 Grafana → 캐시 스탬피드 → 원본 재계산 rate 에서 본다.',
      '',
    ].join('\n'),
  };
}
