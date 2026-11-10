#!/usr/bin/env python3
"""v1.11.146 fishing contracts. Static/source checks; full Gradle+device tests still needed."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET
r=Path(__file__).resolve().parents[1]
java=r/'app/src/main/java/ru/reader/rsvp'
res=r/'app/src/main/res'
source=(java/'ChteicoFishingView.java').read_text()
store=(java/'ChteicoStore.java').read_text()
helptext=(java/'ActivityInfo.java').read_text()
stats=(java/'ChteicoActivity.java').read_text()
layout=ET.parse(res/'layout/view_chteico_fishing.xml').getroot()
ns='{http://schemas.android.com/apk/res/android}'
ids={v.get(ns+'id') for v in layout.iter()}
assert "versionCode 14600" in (r/'app/build.gradle').read_text()
assert "versionName '1.11.146-beta'" in (r/'app/build.gradle').read_text()
# Taps outside the result button do NOT dismiss results.
assert '@+id/fishing_result_continue' in ids
assert 'resultContinue.setOnClickListener(v->closeResult())' in source
assert 'if(phase==RESULT)return true;' in source
assert 'return phase!=RESULT;' in source
assert 'phase=RESULT;held=false' in source
assert 'if(!success)' in source and 'resultFish.setVisibility(GONE)' in source
# Boot catch is genuinely played through; only successful completion records boot.
catch=source[source.index('private void beginCatch()'):source.index('private void showBootResult()')]
assert 'showBootResult()' not in catch
assert 'phase=CATCHING;' in catch
assert 'if(hookedBoot)showBootResult();else successfulCatch();' in source
assert 'hookedBoot?7000:BITE_TIMES[rarity]' in source
assert 'hookedBoot?.65f:SPEED[rarity]' in source
assert 'hookedBoot?.70f:FILL_TIME[rarity]' in source
assert 'hookedBoot?1.2f:CATCH_ZONE_WIDTH[rarity]' in source
# Hidden rarity during catching, real XML art at result.
assert 'ic_fishing_catch_silhouette' in source
assert 'ic_fishing_boot_silhouette' in source
assert 'resultFish.setImageResource(FISH_ART[rarity])' in source
assert 'resultFish.setRotation(rarity==0?-90f:0f)' in source
assert 'CATCH_ZONE_WIDTH={1f,.90f,.80f,.70f,.60f}' in source
# Bobber moves frame by frame; the ripple only follows touchdown.
assert 'phase=FLYING' in source and 'flightProgress+dt/flightDuration' in source
assert 'Math.sin(Math.PI*t)' in source
assert 'phase=FLOATING;vibrate(40);post(this::playWaves)' in source
# Final catch progress is slower than initial progress.
assert 'catchAmount>=.90f?.70f:catchAmount>=.70f?.85f:1f' in source
# 1% shimmering chance, sparkle UI, stats and no change to base rewards.
assert 'random.nextFloat()<.01f' in source
assert 'shimmering?.75f:1f' in source
assert 'shimmering?1.2f:1f' in source
assert 'fishing_catch_shimmer' in source and 'fishing_result_shimmer' in source
assert 'Мерцающая' in source and 'fishing_shimmer_count' in store
assert 'fishing_shimmer_count' in stats
# Rod purchase is persistent, one-time and capped by owned tier.
assert 'FISHING_ROD_PRICES={0,180,800,3000,8000}' in store
assert 'fishing_rod_owned_' in store and 'fishing_rod_equipped' in store
assert 'int maxRarity=ChteicoStore.fishingRodEquipped(getContext());' in source
assert 'for(int i=maxRarity+1;i<weights.length;i++)weights[i]=0d;' in source
assert 'ChteicoStore.purchaseFishingRod(getContext(),rod)' in source
assert 'ChteicoStore.equipFishingRod(getContext(),rod)' in source
# No imported raster graphics; new sprites are Android XML vectors.
required=[
 'ic_fishing_catch_silhouette','ic_fishing_boot_silhouette','ic_fishing_sparkles',
 'ic_fishing_rod_training','ic_fishing_rod_novice','ic_fishing_rod_good',
 'ic_fishing_rod_master','ic_fishing_rod_spinning','ic_fishing_bite_7'
]
for name in required:
    path=res/'drawable'/f'{name}.xml'
    assert path.is_file(),name
    root=ET.parse(path).getroot()
    assert root.tag in ('vector','animated-vector'),name
assert (res/'animator/fishing_bite_7s.xml').is_file()
assert not [p for p in res.rglob('*') if p.suffix.lower() in {'.png','.jpg','.jpeg','.webp','.gif'}]
for xml in res.rglob('*.xml'):ET.parse(xml)
assert 'Экран результата закрывается только кнопкой' in helptext
assert 'спиннинг за 8000' in helptext
print('PASS: v1.11.146 reward, boot catching, black silhouette, flight, slowdown, shimmer, rods and XML')
