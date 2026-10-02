"""Package the APK, setup tool, docs and a verified walking route for local delivery."""
from pathlib import Path
import hashlib, shutil, zipfile
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parent
DIST=ROOT/'dist'
route=(ROOT/'build/verified-walking-route.txt').read_text(encoding='utf-8')
signals=(ROOT/'build/verified-walking-signals.txt').read_text(encoding='utf-8')
gpx=ET.Element('gpx',{'version':'1.1','creator':'Route GPS 1.1.1','xmlns':'http://www.topografix.com/GPX/1/1'})
metadata=ET.SubElement(gpx,'metadata')
ET.SubElement(metadata,'desc').text='OSRM foot route / FOSSGIS. Map data © OpenStreetMap contributors, ODbL. https://www.openstreetmap.org/copyright'
track=ET.SubElement(gpx,'trk');ET.SubElement(track,'name').text='Tokyo station walking loop (OSRM verified)'
segment=ET.SubElement(track,'trkseg')
for line in route.splitlines():
    lat,lon=line.split(',');ET.SubElement(segment,'trkpt',{'lat':lat,'lon':lon})
ET.indent(gpx)
ET.ElementTree(gpx).write(DIST/'sample-tokyo-walking.gpx',encoding='utf-8',xml_declaration=True)
(DIST/'sample-tokyo-walking-signals.txt').write_text(signals+'\n',encoding='utf-8')
adb=DIST/'platform-tools';adb.mkdir(exist_ok=True)
for name in ['adb.exe','AdbWinApi.dll','AdbWinUsbApi.dll','NOTICE.txt','source.properties']:
    shutil.copy2(ROOT/'tools/platform-tools'/name,adb/name)
apk=DIST/'RouteGPS.apk'
assert (DIST/'RouteGPS.apk.sha256').read_text().split()[0]==hashlib.sha256(apk.read_bytes()).hexdigest()
archive=ROOT/'RouteGPS-BlueStacks.zip'
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
    for p in DIST.rglob('*'):
        if p.is_file() and p.suffix!='.idsig':z.write(p,Path('RouteGPS')/p.relative_to(DIST))
with zipfile.ZipFile(archive) as z:
    assert z.testzip() is None
with zipfile.ZipFile(ROOT/'RouteGPS-source.zip','w',zipfile.ZIP_DEFLATED) as z:
    for name in ['AndroidManifest.xml','README.md','build.py','setup_tools.py','package.py','.gitignore']:
        z.write(ROOT/name,Path('route-gps')/name)
    for directory in ['src','res','tests']:
        for p in (ROOT/directory).rglob('*'):
            if p.is_file():z.write(p,Path('route-gps')/p.relative_to(ROOT))
print('Bundle verified: '+str(archive))
print('APK bytes: '+str(apk.stat().st_size))
print('Walking sample points: '+str(len(route.splitlines()))+', signals: '+str(len(signals.splitlines())))
