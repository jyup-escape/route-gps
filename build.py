"""Build a signed, dependency-free APK with javac and official Android build tools."""
from pathlib import Path
import os, subprocess, zipfile, hashlib, shutil

ROOT=Path(__file__).resolve().parent
TOOLS=ROOT/'tools'
JDK=next((TOOLS/'jdk').glob('*/bin/java.exe')).parent
BT=next((TOOLS/'build-tools-33.0.2').glob('*/aapt2.exe')).parent
ANDROID=next((TOOLS/'platforms-android-33').glob('*/android.jar'))
BUILD=ROOT/'build';BUILD.mkdir(exist_ok=True)
CLASSES=BUILD/'classes';shutil.rmtree(CLASSES,ignore_errors=True);CLASSES.mkdir(exist_ok=True)
DEX=BUILD/'dex';shutil.rmtree(DEX,ignore_errors=True);DEX.mkdir(exist_ok=True)
DIST=ROOT/'dist';DIST.mkdir(exist_ok=True)
env=os.environ.copy();env['JAVA_HOME']=str(JDK.parent);env['PATH']=str(JDK)+os.pathsep+env.get('PATH','')
def run(*args):
    subprocess.run([str(a) for a in args],cwd=ROOT,env=env,check=True)
TEST=BUILD/'tests';TEST.mkdir(exist_ok=True)
KXML=TOOLS/'test-kxml2.jar'
if not KXML.exists():raise RuntimeError('Run setup_tools.py to prepare GPX test dependency')
run(JDK/'javac.exe','-encoding','UTF-8','-classpath',KXML,'-d',TEST,*[ROOT/'src/jp/akagumi/routegps'/n for n in ['Route.java','WalkSimulation.java','MapsLink.java','Gpx.java']],*[ROOT/'tests'/n for n in ['RouteTest.java','WalkSimulationTest.java','MapsLinkTest.java','GpxTest.java']])
for test in ['RouteTest','WalkSimulationTest','MapsLinkTest','GpxTest']:run(JDK/'java.exe','-cp',str(TEST)+os.pathsep+str(KXML),test)
run(BT/'aapt2.exe','compile','--dir',ROOT/'res','-o',BUILD/'resources.zip')
RGEN=BUILD/'generated';RGEN.mkdir(exist_ok=True)
run(BT/'aapt2.exe','link','-o',BUILD/'resources.apk','--manifest',ROOT/'AndroidManifest.xml','-I',ANDROID,'--java',RGEN,BUILD/'resources.zip')
sources=list((ROOT/'src').rglob('*.java'))+list(RGEN.rglob('*.java'))
run(JDK/'javac.exe','-encoding','UTF-8','-source','8','-target','8','-classpath',ANDROID,'-d',CLASSES,*sources)
run(JDK/'java.exe','-cp',BT/'lib/d8.jar','com.android.tools.r8.D8','--lib',ANDROID,'--min-api','26','--output',DEX,*CLASSES.rglob('*.class'))
with zipfile.ZipFile(BUILD/'resources.apk') as src,zipfile.ZipFile(BUILD/'unsigned.apk','w',zipfile.ZIP_DEFLATED) as dest:
    for item in src.infolist():dest.writestr(item,src.read(item.filename))
    for dex in DEX.glob('*.dex'):dest.write(dex,dex.name)
run(BT/'zipalign.exe','-f','4',BUILD/'unsigned.apk',BUILD/'aligned.apk')
KEY=TOOLS/'routegps.keystore'
if not KEY.exists():
    run(JDK/'keytool.exe','-genkeypair','-keystore',KEY,'-storepass','routegps-local-build','-keypass','routegps-local-build','-alias','routegps','-keyalg','RSA','-keysize','2048','-validity','10000','-dname','CN=Route GPS Local Build')
APK=DIST/'RouteGPS.apk'
run(JDK/'java.exe','-jar',BT/'lib/apksigner.jar','sign','--ks',KEY,'--ks-pass','pass:routegps-local-build','--out',APK,BUILD/'aligned.apk')
run(JDK/'java.exe','-jar',BT/'lib/apksigner.jar','verify','--verbose',APK)
(DIST/'RouteGPS.apk.sha256').write_text(hashlib.sha256(APK.read_bytes()).hexdigest()+'  RouteGPS.apk\n',encoding='ascii')
print('Built: '+str(APK))
