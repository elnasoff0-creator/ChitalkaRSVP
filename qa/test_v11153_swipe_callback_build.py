#!/usr/bin/env python3
"""Source-level guard for the v172 RIGHT partial-reveal swipe (no swipe dismiss)."""
from pathlib import Path
p=Path(__file__).resolve().parents[1]/'app/src/main/java/ru/reader/rsvp/DownloadActivity.java'
s=p.read_text()
start=s.index('private void attachArchiveSwipe(){')
end=s.index('private final class JobsAdapter',start)
body=s[start:end]
assert 'list.addOnItemTouchListener(' in body
assert 'onInterceptTouchEvent(RecyclerView rv,MotionEvent e)' in body
assert 'onTouchEvent(RecyclerView rv,MotionEvent e)' in body
assert 'ArchiveSwipeMath.horizontal(' in body
assert 'ArchiveSwipeMath.offset(' in body
assert 'ArchiveSwipeMath.reveal(' in body
assert 'ItemTouchHelper' not in body
assert 'revealedSwipeId=open?item.id:""' in body
assert 'swipeActions.setGravity(Gravity.LEFT' in s
assert 'card.setTranslationX(opened?swipeActions.getWidth():0f)' in s
print('PASS: right-swipe gesture and tap release source contract')
