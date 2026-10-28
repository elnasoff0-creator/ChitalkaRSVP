from pathlib import Path
import re
root=Path(__file__).resolve().parents[1]
java=(root/'app/src/main/java/ru/reader/rsvp/MainActivity.java').read_text(encoding='utf-8')
pet=java.split('if(ChteicoStore.enabled(this) && prefs.getBoolean("mini_pet_on_home",true)){',1)[1].split('HomeSectionOrder.apply(this,contentWrap',1)[0]
assert 'new LinearLayout.LayoutParams(dp(cardSize),dp(cardSize))' in pet, 'mini-pet card must be a square'
assert 'areaLp.gravity=Gravity.CENTER_HORIZONTAL' in pet, 'mini-pet must be centered'
assert 'petArea.setBackground(shape(panelColor(),22))' in pet, 'keep shared card color'
assert pet.index('petArea.addView(pet,petLp)') < pet.index('petArea.addView(bubble,bubbleLp)'), 'bubble must overlay the pet'
assert 'new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL)' in pet, 'bubble must be tightly wrapped at fixed top location'
assert 'bubbleLp.topMargin=dp(19)' in pet, 'bubble should cover sleeping eyes'
assert 'ChteicoPhrases' not in pet and 'Reply r=' not in pet, 'no phrases on MainActivity mini-pet'
assert 'emotion.setText(ChteicoEmotions.stroke())' in pet, 'short touch must show an emotion'
assert 'ChteicoEmotions.sleep(phase++)' in pet, 'sleep should animate dots only'
assert 'miniSleepAppearance.accept(true)' in pet and 'ChteicoEmotions.attach(petArea,' in pet
assert 'startActivity(new Intent(this,ChteicoActivity.class))' in pet, 'tap must open full pet'
assert 'HomeSectionOrder::startPetDrag' in pet, 'drag-and-drop must continue working'
assert not re.search(r'(?:reply|speech).setText\(',pet,re.I), 'no speech text should be shown'
print('PASS: centered square mini-pet; only overlaid emotions; sleep down + dots; card gestures')
