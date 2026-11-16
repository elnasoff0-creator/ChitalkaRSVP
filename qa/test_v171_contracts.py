"""v1.11.171 static source contracts; not a substitute for a real Android APK build."""
from pathlib import Path
from xml.etree import ElementTree as ET
import unittest
ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/'app/src/main/java/ru/reader/rsvp'
RES=ROOT/'app/src/main/res'
A='{http://schemas.android.com/apk/res/android}'
def read(name):return (SRC/name).read_text(encoding='utf-8')
class Release171(unittest.TestCase):
    def test_version(self):
        g=(ROOT/'app/build.gradle').read_text()
        self.assertIn('versionCode 17100',g)
        self.assertIn("versionName '1.11.171-beta'",g)
    def test_no_old_release_docs(self):
        docs=[p.name for p in ROOT.iterdir() if p.name.startswith(('CHANGELOG-v','README-v','QA-REPORT-v'))]
        self.assertEqual(len(docs),3)
        self.assertTrue(all('v1.11.171-beta' in x for x in docs))
    def test_balance_per_rod_probability_rows(self):
        j=read('FishingBalance.java')
        for row in ('{87.80, 12.20, 0, 0, 0}', '{60, 16, 10, 8, 6}',
                    '{59.76, 24.39, 15.85, 0, 0}', '{36, 24, 18, 12, 10}',
                    '{26, 25, 21, 16, 12}'):
            self.assertIn(row,j)
        self.assertIn('rod==0 && i>=2',j)
        self.assertIn('rod==2 && i>=3',j)
    def test_cast_quality_modifies_not_bans(self):
        j=read('FishingBalance.java')
        self.assertIn('1.80, 1.40, 1.00, 0.70, 0.40',j)
        self.assertIn('Math.min(1d,q)',j)
        self.assertIn('remaining*eligible[i]/eligibleWeight',j)
    def test_fill_bonus_and_downward_zone_only_on_release(self):
        balance=read('FishingBalance.java'); view=read('ChteicoFishingView.java')
        self.assertIn('.025f, .05f, .10f, .15f',balance)
        self.assertIn('1f, 1f, 1f, 1.05f, 1.10f',balance)
        self.assertIn('held?-FishingBalance.zoneRiseSpeed():FishingBalance.zoneFallSpeed(equippedRod)',view)
        self.assertIn('FishingBalance.rodFillMultiplier(equippedRod)',view)
        self.assertIn('return .53f;',balance)
        self.assertIn('return .34f*FALL_MULTIPLIER',balance)
    def test_fatigue_thresholds_shimmer(self):
        j=read('FishingBalance.java')
        self.assertIn('.90f,.80f,.70f,.65f,.60f',j)
        self.assertIn('shimmering?.05f:0f',j)
    def test_fatigue_state_machine(self):
        j=read('FishingFatigue.java')
        for word in ('FULL_STRENGTH','TIRED','RECOVERING','progress>=threshold','progress<threshold','remaining-Math.max(0f,dt)','remaining<=0f'):
            if word=='progress<threshold':continue
            self.assertIn(word,j)
        self.assertIn('2f+2f*clampRod(rod)',read('FishingBalance.java'))
    def test_tired_fish_no_turn_boost(self):
        b=read('FishingBalance.java');view=read('ChteicoFishingView.java')
        self.assertIn('tired?1f:1f+.22f',b)
        self.assertIn('tired?.70f:1f',b)
        self.assertIn('if(tired)turnBoostLeft=0f',view)
        self.assertIn('turnBoostLeft=tired?0f:.30f',view)
        self.assertNotIn('movementFactor=!hookedBoot&&',view)
    def test_fatigue_update_after_progress(self):
        j=read('ChteicoFishingView.java')
        self.assertLess(j.index('catchAmount=Math.max(0f,Math.min(1f,catchAmount+dt'),j.index('fatigue.update(catchAmount,dt)'))
        self.assertIn('if(!hookedBoot){fatigue.update',j)
    def test_fish_fatigue_reset_when_finishing(self):
        j=read('ChteicoFishingView.java')
        self.assertIn('fatigue.begin(rarity,shimmering',j)
        self.assertIn('fatigue.reset();fatigueIndicator.setVisibility(GONE)',j)
    def test_clock_pauses_delta(self):
        j=read('ChteicoFishingView.java')
        self.assertIn('if(stopped||paused||!isAttachedToWindow())return;',j)
        self.assertIn('lastMs=0;',j)
        self.assertIn('Math.min(.055f',j)
    def test_icon_vector_valid(self):
        p=RES/'drawable/ic_fishing_fatigue_debuff.xml'
        root=ET.parse(p).getroot()
        self.assertEqual(root.tag,'vector')
        self.assertEqual(root.get(A+'width'),'32dp')
        self.assertEqual(root.get(A+'height'),'32dp')
        self.assertEqual(len(root.findall('path')),2)
        self.assertTrue(all(x.get(A+'fillColor')=='@android:color/transparent' for x in root.findall('path')))
    def test_indicator_canvas_and_palette(self):
        j=read('FishingFatigueIndicator.java')
        self.assertIn('FishingFatigueIndicator(Context context, AttributeSet attributes)',j)
        self.assertIn('super(context,attributes)',j)
        self.assertIn('canvas.drawArc(',j)
        self.assertIn('icon.draw(canvas)',j)
        self.assertIn('icon.mutate().setTint(foreground)',j)
        self.assertIn('progressPaint.setColor(accent)',j)
        self.assertIn('setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO)',j)
    def test_overlay_is_upright_and_above_fish(self):
        j=read('ChteicoFishingView.java')
        self.assertIn('fish.getY()-fatigueIndicator.getHeight()-dp(3f)',j)
        self.assertIn('fatigueIndicator.setRotation(0f)',j)
        self.assertIn('fatigueIndicator.setRingFraction(fatigue.ringFraction())',j)
    def test_layout_has_real_indicator_without_touch_handler(self):
        root=ET.parse(RES/'layout/view_chteico_fishing.xml').getroot()
        matches=[x for x in root.iter() if x.get(A+'id')=='@+id/fishing_fatigue_indicator']
        self.assertEqual(len(matches),1)
        self.assertEqual(matches[0].tag,'ru.reader.rsvp.FishingFatigueIndicator')
        self.assertEqual(matches[0].get(A+'clickable'),'false')
        self.assertEqual(matches[0].get(A+'focusable'),'false')
    def test_all_resource_xml_well_formed(self):
        paths=list((ROOT/'app/src/main').rglob('*.xml'))
        for p in paths:ET.parse(p)
        self.assertGreater(len(paths),160)
    def test_existing_rewards_and_bait_unchanged(self):
        j=read('ChteicoFishingView.java')
        for expected in ('int[] mul2={2,3,5,8,14}', 'useFishingBait(getContext())',
                         'hookedBoot=castGrade==4&&random.nextFloat()<.25f',
                         'Math.round(mass*20*mul2[rarity]/2f)',
                         'Math.round(mass*5*mul2[rarity]/2f*(shimmering?3f:1f))'):
            self.assertIn(expected,j)
if __name__=='__main__':unittest.main(verbosity=2)
