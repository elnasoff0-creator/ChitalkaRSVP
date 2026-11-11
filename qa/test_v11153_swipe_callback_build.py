#!/usr/bin/env python3
"""Guard against missing abstract callback in ItemTouchHelper.SimpleCallback."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
s = (root / "app/src/main/java/ru/reader/rsvp/DownloadActivity.java").read_text()
start = s.index("new ItemTouchHelper.SimpleCallback(0,ItemTouchHelper.RIGHT)")
end = s.index("new ItemTouchHelper(callback).attachToRecyclerView(list)", start)
callback = s[start:end]
assert re.search(r"@Override\s+public\s+boolean\s+onMove\s*\(\s*RecyclerView\s+\w+\s*,\s*RecyclerView\.ViewHolder\s+\w+\s*,\s*RecyclerView\.ViewHolder\s+\w+\s*\)", callback), "SimpleCallback must override abstract onMove(RecyclerView, ViewHolder, ViewHolder)"
assert re.search(r"onMove\s*\([^)]*\)\s*\{\s*return\s+false\s*;\s*\}", callback), "Swipe-only callback must reject moves"
assert "@Override public void onSwiped" in callback
assert "ItemTouchHelper.RIGHT" in callback
print("PASS: ItemTouchHelper.SimpleCallback implements onMove and onSwiped")
