from pathlib import Path
import json, subprocess, shutil, sys
sys.setrecursionlimit(20000)
from concurrent.futures import ThreadPoolExecutor
from pypdf import PdfReader, PdfWriter
from PIL import Image,ImageOps,ImageDraw

ROOT=Path(__file__).resolve().parents[2]; TMP=ROOT/'tmp'/'pdfs'
manifest=json.loads((TMP/'manifest.json').read_text(encoding='utf-8'))
pdf=Path(manifest['pdf']);r=PdfReader(pdf)
assert len(r.pages)==manifest['pages']
fields=r.get_fields(); assert len(fields)==manifest['fields']
page_ids={p.indirect_reference.idnum for p in r.pages}
widgets={};link_count=0
for page in r.pages:
    for ref in page.get('/Annots',[]):
        a=ref.get_object();rect=a.get('/Rect')
        if rect:assert 0<=float(rect[0])<=float(rect[2])<=1024 and 0<=float(rect[1])<=float(rect[3])<=768,(a.get('/T'),rect)
        if a.get('/Subtype')=='/Widget':
            name=str(a['/T']);assert name not in widgets,name;widgets[name]=a
            ap=a.get('/AP');assert ap and ap.get('/N') is not None,name
            assert name in fields and fields[name].get('/V','')==a.get('/V',''),name
        if a.get('/Subtype')=='/Link':
            dest=a.get('/Dest');assert dest and dest[0].idnum in page_ids
            link_count+=1
assert len(widgets)==len(fields)
assert link_count==manifest['links']
assert '/JavaScript' not in str(r.trailer['/Root'].get('/Names',{}))
values={'profile-name':'María de prueba','profile-agenda':'Mis Días personales','d01-01-intention':'Dedicar tiempo a mis proyectos y descansar.','d01-01-am':'/Yes','d01-01-water':'6','d01-01-watergoal':'8 vasos','habit-01-h0-d1':'/Yes'}
writer=PdfWriter();writer.clone_document_from_reader(r)
writer.update_page_form_field_values(None,values,auto_regenerate=False)
filled=TMP/'prueba-rellenada.pdf'
with filled.open('wb') as f:writer.write(f)
rr=PdfReader(filled);ff=rr.get_fields()
for name,value in values.items():assert str(ff[name].get('/V'))==value,(name,ff[name])
seen=set()
for p in rr.pages:
    for ref in p.get('/Annots',[]):
        a=ref.get_object();name=a.get('/T')
        if name in values:
            assert str(a.get('/V'))==values[name];assert a['/AP']['/N'] is not None
            if a.get('/FT')=='/Btn':assert str(a.get('/AS'))==values[name]
            seen.add(name)
assert seen==set(values)
print('Estructura, navegación y guardado de campos: correctos.',flush=True)
destinations={row[0]:i+1 for i,row in enumerate(manifest['plan'])}
samples=['home','guide','guide-2','months','weeks','m01','m02','w01-1','d01-01','d02-29','tasks','task-01','note-01','book-01','reflection-01','habit-01','goal-01','settings']
render_dir=TMP/'renders';render_dir.mkdir(exist_ok=True)
poppler=shutil.which('pdftoppm');assert poppler
jobs=[(pdf,destinations[name],name) for name in samples]+[(filled,1,'filled-cover'),(filled,destinations['d01-01'],'filled-day')]
def render(job):
    file,page,name=job
    subprocess.run([poppler,'-f',str(page),'-l',str(page),'-scale-to','1400','-singlefile','-png',str(file),str(render_dir/name)],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.PIPE)
with ThreadPoolExecutor(max_workers=3) as pool:list(pool.map(render,jobs))
sheet=Image.new('RGB',(1400,((len(jobs)+3)//4)*280),'#ece7f2');draw=ImageDraw.Draw(sheet)
for i,(_,_,name) in enumerate(jobs):
    im=Image.open(render_dir/f'{name}.png').convert('RGB');im.thumbnail((340,255))
    x=(i%4)*350;y=(i//4)*280;sheet.paste(im,(x,y+21));draw.text((x+8,y+4),name,fill='#292336')
sheet.save(TMP/'contact-sheet.png')
report={'pages':len(r.pages),'fields':len(fields),'links':link_count,'sample_values_reopened':len(values),'rendered_samples':len(jobs),'size_bytes':pdf.stat().st_size}
(TMP/'verification.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print(json.dumps(report),flush=True)
