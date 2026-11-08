from pathlib import Path
import xml.etree.ElementTree as ET
base=Path(__file__).resolve().parents[1]/'app/src/main/res/drawable'
ns='{http://schemas.android.com/apk/res/android}'
color={'day':'#FFD56B','night':'#A6D7FF','soft':'#82D6A0','sepia':'#E5B58C','custom':'#BC9EF9'}
for theme, fill in color.items():
    for suffix,edge in (('', '#FF151515'),('_dark','#FFFFFFFF')):
        file=base/f'ic_reading_theme_{theme}{suffix}.xml'
        root=ET.parse(file).getroot()
        assert root.get(ns+'width')=='24dp' and root.get(ns+'height')=='24dp'
        assert len(root.findall('path'))>=1
        assert any(p.get(ns+'fillColor')==fill for p in root.findall('path'))
        for p in root.findall('path'):
            assert p.get(ns+'pathData')
            assert p.get(ns+'strokeColor')==edge
            assert float(p.get(ns+'strokeWidth')) <= 1.1
            assert p.get(ns+'fillColor') in [fill, '#00000000', '#F36F90','#F5C65F','#5EB7F6','#78D59C']
print('PASS 10 vector icons: one black/white contour, approved colored fill, transparent canvas')
