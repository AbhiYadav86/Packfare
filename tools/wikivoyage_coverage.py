#!/usr/bin/env python3
"""
Audit how well Wikivoyage covers each Packfare destination.

For every destination in Destinations.kt and ExtraDestinations.kt it asks the Wikivoyage API (the same one
the app uses) whether the article exists, and counts its See/Do/Eat/Sleep listings and how many have rupee
prices. It writes wikivoyage_coverage.csv, thinnest first, so you know where the app needs its own data.

Usage (from the project folder, needs internet, Python 3.8+):
    python3 tools/wikivoyage_coverage.py            # full audit, about 1,000 places, a few minutes
    python3 tools/wikivoyage_coverage.py --list     # only print the titles that would be checked

Be polite: it sends one request at a time with a pause, and identifies itself in the User-Agent.
Put a real contact in USER_AGENT before running it regularly.
"""
import csv
import json
import re
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "app/src/main/java/com/packfare/app/domain"
API = "https://en.wikivoyage.org/w/api.php"
USER_AGENT = "Packfare-coverage-audit/1.0 (personal project; contact: packfare.app)"

PRICE = re.compile(r"(?:₹|\brs\.?|\binr)\s*\d|\d\s*(?:rupees|rs\b|inr\b)", re.I)
LISTING = re.compile(r"\{\{\s*(see|do|eat|drink|sleep|listing)\b", re.I)
STATUS = re.compile(r"\{\{\s*(outline|usable|guide|star)\w*", re.I)


def titles_from_source():
    """(display name, wikivoyage title) for every destination in the Kotlin sources."""
    out = []
    hubs = (SRC / "Destinations.kt").read_text(encoding="utf-8")
    for chunk in hubs.split('Destination("')[1:]:
        name = chunk.split('"', 1)[0]
        statement = chunk.split("Destination(", 1)[0]
        m = re.search(r'wikivoyage\s*=\s*"([^"]+)"', statement)
        out.append((name, m.group(1) if m else name))
    extra = SRC / "ExtraDestinations.kt"
    if extra.exists():
        for line in extra.read_text(encoding="utf-8").splitlines():
            parts = line.strip().strip('"').split("|")
            if len(parts) >= 5 and re.fullmatch(r"-?\d+(\.\d+)?", parts[2] or ""):
                out.append((parts[0], parts[0]))
    seen, uniq = set(), []
    for name, title in out:
        if name not in seen:
            seen.add(name)
            uniq.append((name, title))
    return uniq


def api(params):
    url = API + "?" + urllib.parse.urlencode({**params, "format": "json", "formatversion": "2"})
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r)


def analyse(wikitext):
    kinds = {"see": 0, "do": 0, "eat": 0, "sleep": 0}
    priced = 0
    for m in LISTING.finditer(wikitext):
        kind = m.group(1).lower()
        kind = "eat" if kind == "drink" else kind
        if kind in kinds:
            kinds[kind] += 1
        end = wikitext.find("}}", m.end())
        body = wikitext[m.end(): end if end != -1 else m.end() + 600]
        if PRICE.search(body):
            priced += 1
    status = STATUS.search(wikitext)
    return kinds, priced, status.group(1).lower() if status else "unknown"


def main():
    dests = titles_from_source()
    if "--list" in sys.argv:
        for name, title in dests:
            print(name if name == title else f"{name} -> {title}")
        print(f"\n{len(dests)} destinations", file=sys.stderr)
        return

    rows = []
    for i in range(0, len(dests), 20):
        batch = dests[i:i + 20]
        data = api({
            "action": "query", "prop": "revisions", "rvprop": "content", "rvslots": "main",
            "redirects": "1", "titles": "|".join(t for _, t in batch),
        })
        q = data.get("query", {})
        redirects = {r["from"]: r["to"] for r in q.get("redirects", [])}
        normalized = {n["from"]: n["to"] for n in q.get("normalized", [])}
        pages = {p["title"]: p for p in q.get("pages", [])}
        for name, title in batch:
            resolved = redirects.get(normalized.get(title, title), normalized.get(title, title))
            page = pages.get(resolved)
            if not page or page.get("missing"):
                rows.append([name, title, "MISSING", 0, 0, 0, 0, 0, 0, "unknown"])
                continue
            text = page["revisions"][0]["slots"]["main"]["content"]
            kinds, priced, status = analyse(text)
            listings = sum(kinds.values())
            rows.append([name, resolved, "ok", len(text), listings, kinds["see"] + kinds["do"], kinds["eat"], kinds["sleep"], priced, status])
        print(f"{min(i + 20, len(dests))}/{len(dests)}", file=sys.stderr)
        time.sleep(1.5)

    # Thinnest first: missing, then few listings, then few priced ones.
    rows.sort(key=lambda r: (r[2] != "MISSING", r[4], r[8]))
    out = ROOT / "wikivoyage_coverage.csv"
    with out.open("w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["destination", "wikivoyage_title", "article", "chars", "listings", "see_do", "eat", "sleep", "priced", "status"])
        w.writerows(rows)
    missing = sum(r[2] == "MISSING" for r in rows)
    thin = sum(r[2] == "ok" and (r[4] < 8 or r[8] < 3) for r in rows)
    print(f"\n{len(rows)} destinations: {missing} with no article, {thin} thin (under 8 listings or under 3 prices).")
    print(f"Written to {out}")


if __name__ == "__main__":
    main()
