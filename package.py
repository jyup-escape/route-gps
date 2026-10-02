"""Create versioned release bundles from checked-in docs and samples."""
from pathlib import Path
import hashlib, shutil, zipfile, urllib.request, io
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parent
DIST=ROOT/'dist';DIST.mkdir(exist_ok=True)
version=ET.parse(ROOT/'AndroidManifest.xml').getroot().attrib['{http://schemas.android.com/apk/res/android}versionName']
shutil.copy2(ROOT/'docs/usage.md',DIST/'使い方.md')
for name in ['BlueStacks-Setup.cmd','BlueStacks-Setup.ps1']:
    shutil.copy2(ROOT/'docs'/name,DIST/name)
for p in (ROOT/'samples').iterdir():
    if p.is_file():shutil.copy2(p,DIST/p.name)
if not (ROOT/'tools/platform-tools/adb.exe').exists():
    data=urllib.request.urlopen('https://dl.google.com/android/repository/platform-tools-latest-windows.zip',timeout=120).read()
    zipfile.ZipFile(io.BytesIO(data)).extractall(ROOT/'tools')
adb=DIST/'platform-tools';adb.mkdir(exist_ok=True)
for name in ['adb.exe','AdbWinApi.dll','AdbWinUsbApi.dll','NOTICE.txt','source.properties']:
    shutil.copy2(ROOT/'tools/platform-tools'/name,adb/name)
apk=DIST/'RouteGPS.apk'
assert (DIST/'RouteGPS.apk.sha256').read_text().split()[0]==hashlib.sha256(apk.read_bytes()).hexdigest()
archive=ROOT/'RouteGPS-BlueStacks.zip'
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
    for p in DIST.rglob('*'):
        if p.is_file() and p.suffix!='.idsig':z.write(p,Path('RouteGPS')/p.relative_to(DIST))
with zipfile.ZipFile(archive) as z:assert z.testzip() is None
shutil.copy2(apk,ROOT/('RouteGPS-'+version+'.apk'))
shutil.copy2(archive,ROOT/('RouteGPS-'+version+'-BlueStacks.zip'))
(ROOT/('RouteGPS-'+version+'.apk.sha256')).write_text(hashlib.sha256(apk.read_bytes()).hexdigest()+'  RouteGPS-'+version+'.apk\n',encoding='ascii')
with zipfile.ZipFile(ROOT/'RouteGPS-source.zip','w',zipfile.ZIP_DEFLATED) as z:
    for name in ['AndroidManifest.xml','README.md','CHANGELOG.md','build.py','setup_tools.py','package.py','.gitignore']:
        z.write(ROOT/name,Path('route-gps')/name)
    for directory in ['src','res','tests','docs','samples','.github']:
        for p in (ROOT/directory).rglob('*'):
            if p.is_file():z.write(p,Path('route-gps')/p.relative_to(ROOT))
print('Bundle verified: '+str(archive))
print('Release version: '+version)
