"""Package only the APK and its build sources. Historical refs retain their behaviour."""
from pathlib import Path
import shutil, zipfile, hashlib, subprocess, argparse, xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parent
parser=argparse.ArgumentParser()
parser.add_argument('--ref', help='Saved source ref; APK must already be rebuilt from this ref')
args=parser.parse_args()
def git(*argv):
    return subprocess.check_output(['git', *argv], cwd=ROOT)
if args.ref:
    manifest=git('show',args.ref+':AndroidManifest.xml')
else:
    manifest=(ROOT/'AndroidManifest.xml').read_bytes()
version=ET.fromstring(manifest).attrib['{http://schemas.android.com/apk/res/android}versionName']
destination=ROOT/'releases'/version
destination.mkdir(parents=True,exist_ok=True)
apk=ROOT/'dist/RouteGPS.apk'
checksum=hashlib.sha256(apk.read_bytes()).hexdigest()
assert (ROOT/'dist/RouteGPS.apk.sha256').read_text().split()[0]==checksum
shutil.copy2(apk,destination/f'RouteGPS-{version}.apk')
archive=destination/f'RouteGPS-{version}-source.zip'
if args.ref:
    names=git('ls-tree','-r','--name-only',args.ref).decode().splitlines()
else:
    names=git('ls-files','--cached','--others','--exclude-standard').decode().splitlines()
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
    for name in sorted(set(names)):
        if name.startswith(('tools/','build/','dist/','releases/')) or 'BlueStacks' in name or name.endswith(('.apk','.zip','.keystore','.sha256')):
            continue
        data=git('show',args.ref+':'+name) if args.ref else (ROOT/name).read_bytes()
        z.writestr('route-gps/'+name,data)
with zipfile.ZipFile(archive) as z:assert z.testzip() is None
print(f'Packaged {version}: APK SHA-256 {checksum}')
print(destination)
