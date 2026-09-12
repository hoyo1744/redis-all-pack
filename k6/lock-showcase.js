// 대시보드로 **눈으로 보기 위한** 시나리오.
//
// 락과 쓰기 방식을 60초씩 순서대로 갈아끼운다. 한 번에 하나만 돌아가므로
// Grafana 에서 구간이 띠처럼 나뉘어 보인다 — 어느 구간에서 무엇이 달라지는지 그대로 읽힌다.
//
//   docker compose -f docker/monitoring-compose.yml up -d
//   ./gradlew :apps:redis-all-pack:bootRun
//   k6 run k6/lock-showcase.js
//
// k6 자체 웹 대시보드도 같이 보려면:
//   K6_WEB_DASHBOARD=true K6_WEB_DASHBOARD_EXPORT=k6-report.html k6 run k6/lock-showcase.js
//   → http://localhost:5665
//
// Grafana: http://localhost:3000 (admin/admin) → "분산락 — 락 · 쓰기 방식 · 펜싱 토큰"
//
// ⚠️ 마지막 구간(뚫림 재현)은 **lease 를 짧게 준 앱**이어야 보인다:
//   COUPON_LOCK_LEASE_MILLIS=200 COUPON_LOCK_WAIT_MILLIS=2000 ./gradlew :apps:redis-all-pack:bootRun
import http from 'k6/http';
import { Counter } from 'k6/metrics';

const BASE = __ENV.BASE || 'http://localhost:8080';
const VUS = Number(__ENV.VUS || 60);
const SPAN = __ENV.SPAN || '60s';
const GAP = Number(__ENV.GAP || 15); // 구간 사이 공백(초). 대시보드에서 경계가 선명해진다.

// 각 구간이 무엇을 보여주는가.
const STAGES = [
  { key: 'a_none_plain', lock: 'NONE', write: 'PLAIN', pause: 0,
    story: '락 없음 — 허수 응답(HTTP 200 ≫ DB 실제)이 난다' },
  { key: 'b_spin', lock: 'SPIN', write: 'PLAIN', pause: 0,
    story: '스핀락 — p95 가 대기 예산(500ms)에 붙고 429 가 섞인다' },
  { key: 'c_redisson', lock: 'REDISSON', write: 'PLAIN', pause: 0,
    story: 'Redisson — 타임아웃 0, 대신 처리량이 낮다' },
  { key: 'd_redlock', lock: 'REDLOCK', write: 'PLAIN', pause: 0,
    story: 'RedLock — 과반을 모으느라 가장 느리다' },
  { key: 'e_none_atomic', lock: 'NONE', write: 'ATOMIC', pause: 0,
    story: '락 없이 조건부 UPDATE — 가장 빠르면서 정확하다' },
  { key: 'f_breach', lock: 'REDISSON', write: 'FENCED', pause: 1000,
    story: '뚫림 재현 — stale_fence 선이 솟는다 (lease 200ms 앱 필요)' },
];

function scenarios() {
  const out = {};
  const spanSec = parseInt(SPAN, 10);

  STAGES.forEach((s, i) => {
    out[s.key] = {
      executor: 'constant-vus',
      vus: VUS,
      duration: SPAN,
      startTime: `${i * (spanSec + GAP)}s`,
      exec: 'issue',
      // 태그를 달아두면 k6 요약에서도 구간별로 갈린다.
      tags: { stage: s.key, lock: s.lock, write: s.write },
      env: { STAGE_INDEX: String(i) },
    };
  });

  return out;
}

export const options = { scenarios: scenarios() };

const issued = new Counter('showcase_issued');
const rejected = new Counter('showcase_rejected');

// 구간마다 쿠폰을 새로 만든다. **재사용하면 앞 구간이 소진시켜 다음 구간이 소진만 보게 된다.**
// 그리고 last_fence 가 이미 올라가 있어 펜싱 구간이 통째로 튕긴다.
const RUN = `SHOW${Date.now()}`;

export function setup() {
  // 앱 설정을 먼저 읽는다. **lease 가 정지 시간보다 길면 뚫림 구간이 통째로 빈다** —
  // 락이 만료되지 않아 지각 쓰기가 아예 안 생기고, 대시보드에는 아무 일도 안 일어난 것처럼 보인다.
  // 조용히 비는 것보다 시작 전에 말해주는 게 낫다.
  const settings = http.get(`${BASE}/api/v1/coupons/settings`);
  const lease = settings.status === 200 ? Number(settings.json('leaseMillis')) : null;
  const maxPause = Math.max(...STAGES.map((s) => s.pause));

  if (lease !== null) {
    console.log(`앱 설정: lease=${lease}ms  토큰출처=${settings.json('fenceSource')}`);

    if (maxPause > 0 && lease >= maxPause) {
      console.warn(
        `⚠️  lease(${lease}ms) 가 최대 정지(${maxPause}ms) 보다 길어 뚫림 구간이 비어 있을 것이다.\n`
        + '    지각 쓰기를 보려면 앱을 이렇게 띄울 것:\n'
        + '    COUPON_LOCK_LEASE_MILLIS=200 COUPON_LOCK_WAIT_MILLIS=2000 ./gradlew :apps:redis-all-pack:bootRun',
      );
    }
  }

  STAGES.forEach((s) => {
    http.post(
      `${BASE}/api/v1/coupons`,
      JSON.stringify({ couponId: `${RUN}-${s.key}`, name: s.story, totalQuantity: 1000000 }),
      { headers: { 'Content-Type': 'application/json' } },
    );
  });

  return { run: RUN, lease };
}

export function issue(data) {
  const s = STAGES[Number(__ENV.STAGE_INDEX)];
  const url = `${BASE}/api/v1/coupons/${data.run}-${s.key}/issue`
    + `?lock=${s.lock}&write=${s.write}&pause=${s.pause}`;

  const res = http.post(
    url,
    JSON.stringify({ userId: `u-${__VU}-${__ITER}` }),
    { headers: { 'Content-Type': 'application/json' }, tags: { stage: s.key } },
  );

  if (res.status === 200) issued.add(1, { stage: s.key });
  else rejected.add(1, { stage: s.key });
}

export function handleSummary(data) {
  const lines = ['', '구간별 시나리오', '─'.repeat(70)];

  STAGES.forEach((s, i) => {
    lines.push(`${i + 1}. ${s.key.padEnd(14)} lock=${s.lock.padEnd(9)} write=${s.write.padEnd(7)} pause=${s.pause}ms`);
    lines.push(`   ${s.story}`);
  });

  lines.push('', 'Grafana 에서 확인:  http://localhost:3000  →  분산락 대시보드', '');
  lines.push('발급 성공 수량을 반드시 DB 로 확인할 것 — HTTP 200 과 다르면 그게 허수다:');
  STAGES.forEach((s) => {
    lines.push(`  curl ${BASE}/api/v1/coupons/${RUN}-${s.key}/remaining`);
  });
  lines.push('');

  return { stdout: lines.join('\n') };
}
