#!/usr/bin/env python3
"""측정 결과를 표로 모은다.

k6 요약(JSON)과 실행별 카운터(txt), DB 실제 수량(psv)이 따로 나오므로 한 곳에 합친다.
숫자를 손으로 옮기면 반드시 틀린다.

  python3 k6/collect.py /tmp/lock-comparison
"""
import json
import os
import re
import sys

d = sys.argv[1] if len(sys.argv) > 1 else "/tmp/lock-comparison"

COUNTER_LABELS = {
    "발급 성공": "ok",
    "소진": "soldout",
    "락 타임아웃": "timeout",
    "지각 쓰기 차단": "stale",
}


def counters(tag, slug):
    """실행별 txt 에서 카운터를 읽는다."""
    path = os.path.join(d, f"{tag}-{slug}.txt")
    out = dict.fromkeys(COUNTER_LABELS.values(), 0)
    if not os.path.exists(path):
        return out
    for line in open(path, encoding="utf-8"):
        for ko, key in COUNTER_LABELS.items():
            m = re.match(rf"^{ko}\s+(\d+)", line.strip())
            if m:
                out[key] = int(m.group(1))
    return out


def latency(tag, slug):
    path = os.path.join(d, f"{tag}-{slug}.json")
    if not os.path.exists(path):
        return {}
    m = json.load(open(path))["metrics"]
    dur = m.get("http_req_duration", {})
    dur = dur.get("values", dur)
    reqs = m.get("http_reqs", {})
    reqs = reqs.get("values", reqs)
    return {
        "rps": reqs.get("rate", 0),
        "reqs": reqs.get("count", 0),
        "avg": dur.get("avg", 0),
        "p95": dur.get("p(95)", 0),
        "max": dur.get("max", 0),
    }


def rows(tag):
    """psv: label|qty|issued|remaining"""
    path = os.path.join(d, f"{tag}-summary.psv")
    if not os.path.exists(path):
        return []
    out = []
    for line in open(path, encoding="utf-8"):
        line = line.strip()
        if not line:
            continue
        label, qty, issued, rem = line.split("|")
        slug = re.sub(r"[ /+]", "_", label)
        out.append({
            "label": label, "qty": int(qty), "issued": int(issued), "remaining": int(rem),
            **counters(tag, slug), **latency(tag, slug),
        })
    return out


def table(title, tag, cols, headers):
    data = rows(tag)
    if not data:
        return
    print(f"\n### {title}\n")
    print("| " + " | ".join(headers) + " |")
    print("|" + "|".join(["---"] * len(headers)) + "|")
    for r in data:
        cells = []
        for c in cols:
            v = r.get(c, 0)
            if isinstance(v, float):
                cells.append(f"{v:,.1f}")
            elif isinstance(v, int):
                cells.append(f"{v:,}")
            else:
                cells.append(str(v))
        print("| " + " | ".join(cells) + " |")


table(
    "정확성 — 수량 100, 소진시킴", "correctness",
    ["label", "ok", "issued", "soldout", "timeout", "stale"],
    ["락", "HTTP 200", "DB 실제", "소진", "락 타임아웃", "지각 차단"],
)
table(
    "쓰기 방식 — 락 없이(NONE), 수량 100", "write",
    ["label", "ok", "issued", "soldout", "stale"],
    ["쓰기 방식", "HTTP 200", "DB 실제", "소진", "지각 차단"],
)
table(
    "성능 — 수량 100만, 소진 안 됨", "perf",
    ["label", "rps", "avg", "p95", "max", "timeout", "ok"],
    ["락", "rps", "avg ms", "p95 ms", "max ms", "락 타임아웃", "발급"],
)
table(
    "락 뚫림 재현 — lease 200ms + 임계구역 1초 정지", "breach",
    ["label", "ok", "issued", "stale", "timeout"],
    ["구성", "HTTP 200", "DB 실제", "지각 차단", "락 타임아웃"],
)
print()
