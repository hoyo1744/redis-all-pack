#!/usr/bin/env python3
"""펜싱 토큰 출처 두 구현을 한 표로 합친다.

  python3 k6/collect-fence.py /tmp/fc-redis /tmp/fc-rdb
"""
import json
import os
import re
import sys

LABELS = {"발급 성공": "ok", "소진": "soldout", "락 타임아웃": "timeout", "지각 쓰기 차단": "stale"}


def read(d, lock):
    out = dict.fromkeys(LABELS.values(), 0)
    txt = os.path.join(d, f"fence-{lock}.txt")
    if os.path.exists(txt):
        for line in open(txt, encoding="utf-8"):
            for ko, key in LABELS.items():
                m = re.match(rf"^{ko}\s+(\d+)", line.strip())
                if m:
                    out[key] = int(m.group(1))

    js = os.path.join(d, f"fence-{lock}.json")
    if os.path.exists(js):
        m = json.load(open(js))["metrics"]
        dur = m["http_req_duration"]
        dur = dur.get("values", dur)
        rq = m["http_reqs"]
        rq = rq.get("values", rq)
        out.update(rps=rq.get("rate", 0), avg=dur.get("avg", 0), p95=dur.get("p(95)", 0))

    psv = os.path.join(d, "fence-summary.psv")
    if os.path.exists(psv):
        for line in open(psv, encoding="utf-8"):
            if not line.strip():
                continue
            label, qty, issued, rem = line.strip().split("|")
            if label.endswith(f"/{lock}"):
                out.update(source=label.split("/")[0], issued=int(issued))
    return out


print("\n### 펜싱 토큰 출처 비교 — 수량 100만(소진 없음), WRITE=FENCED\n")
print("| 토큰 출처 | 락 | rps | avg ms | p95 ms | HTTP 200 | DB 실제 | 지각 차단 | 락 타임아웃 |")
print("|---|---|---|---|---|---|---|---|---|")
for d in sys.argv[1:]:
    for lock in ("NONE", "REDISSON"):
        r = read(d, lock)
        if not r.get("source"):
            continue
        print(
            f"| {r['source']} | {lock} | {r.get('rps', 0):,.1f} | {r.get('avg', 0):,.1f} "
            f"| {r.get('p95', 0):,.1f} | {r['ok']:,} | {r.get('issued', 0):,} "
            f"| {r['stale']:,} | {r['timeout']:,} |"
        )
print()
