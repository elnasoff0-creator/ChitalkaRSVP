#!/usr/bin/env python3
"""Regression: feeding belongs only to the enabled pet screen, not statistics-only mode."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
source = (root / 'app/src/main/java/ru/reader/rsvp/ChteicoActivity.java').read_text(encoding='utf-8')
disabled, enabled = source.split('private void showDisabledSummary(){', 1)[1].split('private void show(){', 1)
enabled = enabled.split('private void addGameStatistics(){', 1)[0]
assert 'if(stage' not in disabled, 'stage is local to show(), not showDisabledSummary()'
assert 'ChteicoStore.feed(' not in disabled, 'feeding must not appear when the pet is disabled'
assert 'if(!ChteicoStore.enabled(this)){showDisabledSummary();return;}' in enabled
assert enabled.index('int stage=ChteicoStore.stage(this);') < enabled.index('if(stage!=ChteicoStore.EGG){')
assert enabled.count('ChteicoStore.feed(this)') == 1
assert source.count('ChteicoStore.feed(this)') == 1
assert enabled.rindex('if(stage!=ChteicoStore.EGG){') < enabled.index('ChteicoStore.feed(this)')
print('PASS: feeding only on enabled non-egg pet screen; no duplicate block or out-of-scope stage reference')
