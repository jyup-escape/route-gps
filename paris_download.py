from pathlib import Path
import urllib.request, json, datetime

root=Path(__file__).resolve().parent/'tools/paris'
root.mkdir(exist_ok=True)
url='https://eu.ftp.opendatasoft.com/stif/GTFS/IDFM-gtfs.zip'
with urllib.request.urlopen(url,timeout=120) as response:
    modified=response.headers.get('Last-Modified')
    print('GTFS bytes:',response.headers.get('Content-Length'),'Last-Modified:',modified,flush=True)
    count=last=0
    with (root/'IDFM-gtfs.zip').open('wb') as out:
        while chunk:=response.read(1024*1024):
            out.write(chunk);count+=len(chunk)
            if count-last>50*1024*1024:
                print(str(count//1024//1024)+' MB downloaded',flush=True);last=count
(root/'source.json').write_text(json.dumps({'url':url,'lastModified':modified,'downloadedUtc':datetime.datetime.now(datetime.timezone.utc).isoformat()},ensure_ascii=False),encoding='utf-8')
print('Download complete',flush=True)
