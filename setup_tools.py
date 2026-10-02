"""Download official Android tools and an Eclipse Temurin JDK locally."""
from pathlib import Path
import concurrent.futures, hashlib, json, urllib.request, zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent / 'tools'
ROOT.mkdir(exist_ok=True)
def fetch(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent':'RouteGPS-build'}), timeout=120) as r:
        return r.read()
def local(el, tag):
    return next((e for e in el if e.tag.split('}')[-1] == tag), None)
repo = ET.fromstring(fetch('https://dl.google.com/android/repository/repository2-1.xml'))
jobs = []
for package in repo:
    path = package.attrib.get('path')
    if path not in ('platforms;android-33', 'build-tools;33.0.2'):
        continue
    for archive in local(package, 'archives'):
        host = local(archive, 'host-os')
        if host is not None and host.text != 'windows':
            continue
        complete = local(archive, 'complete')
        url = local(complete, 'url').text
        checksum = local(complete, 'checksum').text
        name=path.replace(';','-')
        if not any(job[0]==name for job in jobs):
            jobs.append((name, 'https://dl.google.com/android/repository/'+url, checksum, 'sha1'))
        break
assets = json.loads(fetch('https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&image_type=jdk&os=windows'))
jdk = assets[0]['binary']['package']
jobs.append(('jdk', jdk['link'], jdk['checksum'], 'sha256'))
def install(job):
    name, url, checksum, algorithm = job
    dest = ROOT/name
    if (dest/'.ready').exists():
        return str(dest)
    print('Downloading '+name, flush=True)
    data = fetch(url)
    if hashlib.new(algorithm, data).hexdigest().lower() != checksum.lower():
        raise RuntimeError('Checksum mismatch: '+name)
    archive = ROOT/(name+'.zip')
    archive.write_bytes(data)
    dest.mkdir(exist_ok=True)
    with zipfile.ZipFile(archive) as z:
        z.extractall(dest)
    (dest/'.ready').write_text(url, encoding='utf-8')
    print('Ready: '+name, flush=True)
    return str(dest)
with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
    for result in pool.map(install, jobs):
        print(result, flush=True)

# JVM-only XmlPull implementation for GPX tests; APK uses Android standard API.
kxml_url='https://repo.maven.apache.org/maven2/net/sf/kxml/kxml2/2.3.0/kxml2-2.3.0.jar'
kxml=fetch(kxml_url)
if hashlib.sha1(kxml).hexdigest()!=fetch(kxml_url+'.sha1').decode().split()[0]:raise RuntimeError('KXML checksum mismatch')
(ROOT/'test-kxml2.jar').write_bytes(kxml)

# JVM-only org.json; the APK uses Android standard API.
json_url="https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar"
json_jar=fetch(json_url)
if hashlib.sha1(json_jar).hexdigest()!=fetch(json_url+".sha1").decode().split()[0]:raise RuntimeError("JSON checksum mismatch")
(ROOT/"test-json.jar").write_bytes(json_jar)
