from pathlib import Path
import csv,io,zipfile,collections
z=zipfile.ZipFile(Path(__file__).resolve().parent/'tools/paris/IDFM-gtfs.zip')
print([(n,z.getinfo(n).file_size) for n in z.namelist()])
def rows(name):return csv.DictReader(io.TextIOWrapper(z.open(name),encoding='utf-8-sig'))
for name in ['agency.txt','routes.txt','trips.txt','shapes.txt','stop_times.txt','feed_info.txt','calendar.txt','frequencies.txt']:
    if name in z.namelist():
        r=rows(name);print(name,r.fieldnames);print(next(r,None))
rail={r['route_id']:r for r in rows('routes.txt') if r['route_type'] in ['1','2']}
print('RAIL ROUTES',[(r['route_id'],r['route_short_name'],r['route_long_name'],r['agency_id']) for r in rail.values()])
trips=collections.Counter();shape=collections.Counter()
for row in rows('trips.txt'):
    if row['route_id'] in rail:
        trips[row['route_id']]+=1
        if row.get('shape_id'):shape[row['route_id']]+=1
print('TRIPS',[(rail[k]['route_short_name'],v,'shapes',shape[k]) for k,v in trips.items()])
