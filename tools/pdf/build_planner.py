from pathlib import Path
import calendar, json, textwrap
from reportlab.pdfgen import canvas
from reportlab.lib.colors import HexColor, Color, white
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'output'/'pdf'; OUT.mkdir(parents=True,exist_ok=True)
TMP=ROOT/'tmp'/'pdfs'; TMP.mkdir(parents=True,exist_ok=True)
PDF=OUT/'Mis-Dias-agenda-interactiva.pdf'
import reportlab
FONT_DIR=Path(r'C:\Windows\Fonts')
if (FONT_DIR/'arial.ttf').exists():
    regular,bold=FONT_DIR/'arial.ttf',FONT_DIR/'arialbd.ttf'
else:
    FONT_DIR=Path(reportlab.__file__).parent/'fonts'
    regular,bold=FONT_DIR/'Vera.ttf',FONT_DIR/'VeraBd.ttf'
pdfmetrics.registerFont(TTFont('Arial',str(regular)))
pdfmetrics.registerFont(TTFont('ArialBold',str(bold)))
W,H=1024,768
BG=HexColor('#F5F0FB'); ACC=HexColor('#75539B'); LIGHT=HexColor('#E8DDF7')
INK=HexColor('#292336'); MUTED=HexColor('#665B73'); LINE=HexColor('#DED4EB')
MONTHS=['Enero','Febrero','Marzo','Abril','Mayo','Junio','Julio','Agosto','Septiembre','Octubre','Noviembre','Diciembre']
NAV=[('Inicio','home'),('Calendario','months'),('Semanas','weeks'),('Días','days'),('Tareas','tasks'),('Notas','notes'),('Cuadernos','books'),('Diario','diary'),('Hábitos','habits'),('Metas','goals'),('Guía','guide')]
plan=[('home','home',None),('guide','guide',0),('guide-2','guide',1),('months','month-index',None),('weeks','week-index',None),('days','day-index',None)]
for m in range(1,13):
    plan.append((f'm{m:02}','month',m))
    for w in range(1,7): plan.append((f'w{m:02}-{w}','week',(m,w)))
    for d in range(1,calendar.monthrange(2028,m)[1]+1): plan.append((f'd{m:02}-{d:02}','day',(m,d)))
for key,kind in [('tasks','task'),('notes','note'),('books','book'),('diary','reflection'),('habits','habit'),('goals','goal')]:
    plan.append((key,'section-index',kind))
    for n in range(1,13): plan.append((f'{kind}-{n:02}',kind,n))
plan.append(('settings','settings',None))
c=canvas.Canvas(str(PDF),pagesize=(W,H),pageCompression=1)
c.setTitle('Mis Días | Agenda digital interactiva sin año fijo')
c.setAuthor('Mis Días'); c.setSubject('Agenda rellenable con enlaces internos, planificación y autocuidado')
c.setViewerPreference('DisplayDocTitle','true')
fields=[]; links=[]

def text(t,x,y,size=12,bold=False,color=INK):
    c.setFillColor(color);c.setFont('ArialBold' if bold else 'Arial',size);c.drawString(x,y,t)
def wrap(t,x,y,width=760,size=12,leading=18,color=MUTED):
    words=t.split(); line=''
    for word in words:
        nxt=f'{line} {word}'.strip()
        if pdfmetrics.stringWidth(nxt,'Arial',size)>width and line:
            text(line,x,y,size,color=color); y-=leading;line=word
        else: line=nxt
    if line:text(line,x,y,size,color=color);y-=leading
    return y
def box(x,y,w,h,fill=white):
    c.setFillColor(fill);c.setStrokeColor(LINE);c.setLineWidth(.7);c.roundRect(x,y,w,h,12,fill=1,stroke=1)
def button(label,dest,x,y,w=100,h=30,selected=False):
    c.setFillColor(ACC if selected else LIGHT);c.roundRect(x,y,w,h,8,fill=1,stroke=0)
    size=10 if len(label)>15 else 11
    text(label,x+10,y+(h-size)/2+2,size,bold=True,color=white if selected else ACC)
    c.linkRect(label,dest,(x,y,x+w,y+h),relative=0,thickness=0);links.append(dest)
def field(name,label,x,y,w,h,multi=False,value='',size=11,maxlen=3000):
    assert name not in fields,name
    fields.append(name)
    if label:text(label,x,y+h+8,10,bold=True,color=MUTED)
    c.acroForm.textfield(name=name,tooltip=label or name,value=value,x=x,y=y,width=w,height=h,
        fontName='Helvetica',fontSize=size,textColor=INK,fillColor=white,borderColor=LINE,
        borderWidth=.7,borderStyle='solid',forceBorder=True,fieldFlags='multiline' if multi else '',maxlen=maxlen)
def check(name,label,x,y):
    fields.append(name)
    c.acroForm.checkbox(name=name,tooltip=label,x=x,y=y,size=18,checked=False,
        buttonStyle='check',borderWidth=1,borderColor=ACC,fillColor=white,textColor=ACC,forceBorder=True)
    text(label,x+26,y+4,10)
def header(title,sub='',active='',i=0):
    c.setFillColor(BG);c.rect(0,0,W,H,fill=1,stroke=0)
    c.setFillColor(LIGHT);c.rect(0,0,150,H,fill=1,stroke=0)
    text('Mis Días',18,724,24,True,color=ACC);text('TU ESPACIO PERSONAL',18,702,8,color=MUTED)
    for j,(label,dest) in enumerate(NAV):
        yy=645-j*44
        if dest==active:c.setFillColor(ACC);c.roundRect(10,yy-7,130,34,8,fill=1,stroke=0)
        text(label,25,yy+4,12,dest==active,color=white if dest==active else INK)
        c.linkRect(label,dest,(10,yy-7,140,yy+27),thickness=0);links.append(dest)
    button('Mi perfil','settings',16,62,118,30)
    text('SIN AÑO FIJO',24,32,9,color=MUTED)
    text(title,182,713,27,True)
    if sub:text(sub,184,687,11,color=MUTED)
    c.setStrokeColor(LINE);c.line(182,669,992,669)
    text(f'Mis Días  |  {i+1} / {len(plan)}',184,22,9,color=MUTED)
    if i:button('Anterior',plan[i-1][0],788,10,92,28)
    if i+1<len(plan):button('Siguiente',plan[i+1][0],890,10,102,28)
def month_nav(m):
    for j,label in enumerate(MONTHS):button(label[:3],f'm{j+1:02}',182+j*68,628,62,28,selected=j+1==m)
def lines(x,y,w,h,step=25):
    c.setStrokeColor(LINE);c.setLineWidth(.6)
    yy=y+20
    while yy<y+h:c.line(x+12,yy,x+w-12,yy);yy+=step
def section_index(kind):
    titles={'task':('Tareas','Listas para avanzar a tu ritmo.'),'note':('Notas','Texto rellenable y espacio para ideas.'),'book':('Cuadernos','Doce portadas con su propia hoja de escritura.'),'reflection':('Diario','Un lugar para escuchar cómo estás.'),'habit':('Hábitos','Seguimiento mensual, sin exigir perfección.'),'goal':('Metas','Haz espacio para lo que quieres construir.')}
    title,sub=titles[kind]
    for n in range(1,13):
        col=(n-1)%3;row=(n-1)//3
        x=184+col*274;y=520-row*125
        box(x,y,258,104)
        text(f'{title} {n:02}',x+18,y+70,17,True)
        field(f'index-{kind}-{n}','',x+18,y+35,220,24,size=10,maxlen=80)
        button('Abrir',f'{kind}-{n:02}',x+154,y+5,86,24)

for i,(dest,kind,data) in enumerate(plan):
    c.bookmarkPage(dest)
    titles={'home':'Tu espacio, a tu ritmo','guide':'Cómo usar tu agenda','month-index':'Tu año, mes a mes','week-index':'Planifica tu semana','day-index':'Un día para ti','section-index':dict(task='Tareas',note='Notas',book='Cuadernos',reflection='Diario',habit='Hábitos',goal='Metas').get(data,''),'settings':'Hazla tuya'}
    active={'home':'home','guide':'guide','month-index':'months','week-index':'weeks','day-index':'days','month':'months','week':'weeks','day':'days','task':'tasks','note':'notes','book':'books','reflection':'diary','habit':'habits','goal':'goals','settings':''}.get(kind, {'task':'tasks','note':'notes','book':'books','reflection':'diary','habit':'habits','goal':'goals'}.get(data,''))
    if kind in ['month','week','day']:
        m=data if kind=='month' else data[0]
        title=MONTHS[m-1] if kind=='month' else f'{MONTHS[m-1]} · Semana {data[1]}' if kind=='week' else f'{data[1]} de {MONTHS[m-1].lower()}'
    else:title=titles.get(kind,dict(task='Mi lista de tareas',note='Una página para tus ideas',book='Mi cuaderno',reflection='Mi diario',habit='Pequeños hábitos',goal='Una meta con intención').get(kind,''))
    header(title,'Agenda perpetua: escribe el año y las fechas que necesites.',active,i)
    if kind in ['home','month-index','week-index','day-index','section-index','settings']: c.addOutlineEntry(title,dest,level=0)
    if kind=='home':
        box(184,400,806,244,LIGHT)
        text('Hay espacio para todo lo que eres.',208,602,24,True,color=ACC)
        wrap('Organiza tus planes, guarda tus ideas y encuentra un momento para cuidarte. Esta agenda es tuya: úsala a tu manera.',208,567,730,14,21)
        field('profile-agenda','Nombre de mi agenda',208,468,354,39,value='Mis Días',size=17,maxlen=60)
        field('profile-name','Esta agenda pertenece a',586,468,378,39,size=17,maxlen=60)
        field('profile-year','Año / periodo',208,416,198,26,size=12,maxlen=40)
        text('Empieza donde estés',184,363,19,True)
        for j,(label,link) in enumerate([('Calendario','months'),('Mi día','days'),('Mi diario','diary'),('Notas','notes'),('Metas','goals'),('Cómo usarla','guide')]):
            button(label,link,184+(j%3)*274,293-(j//3)*62,258,46)
        wrap('Consejo: conserva una copia limpia y trabaja sobre una copia con tu nombre y año. Así podrás reutilizarla cada año.',184,180,788,12,19)
        text('Privada. Sin cuentas. Tus archivos, bajo tu control.',184,104,12,True,color=ACC)
    elif kind=='guide':
        blocks=([
            ('01  Navega con un toque','Las pestañas de la izquierda abren cada sección. El calendario lleva a cada mes; los botones numerados de un mes abren sus páginas diarias. Anterior y Siguiente recorren el documento.'),
            ('02  Rellena o escribe con lápiz','Los recuadros blancos son campos rellenables. Las casillas se pueden marcar. Para dibujar o escribir a mano sobre cualquier página, activa la herramienta de anotación del lector PDF.'),
            ('03  Guarda tus cambios','Guarda una copia local y comprueba que el texto y las marcas siguen ahí después de cerrar y volver a abrir el archivo. Cada lector maneja el guardado de una manera diferente.'),
            ('04  Usa solo las páginas que necesites','Hay 12 meses, 72 semanas sin fechar y 366 páginas diarias. Febrero incluye un día 29 opcional. Las semanas no siguen una numeración ISO: escribe su periodo.')
        ] if data==0 else [
            ('Compatibilidad','Necesitas un lector que conserve formularios PDF para rellenar campos, y un lector con anotaciones para usar lápiz o dedo. El visor del navegador puede mostrar el PDF sin permitir editarlo.'),
            ('Una agenda sin año fijo','Escribe el año y coloca las fechas en el calendario mensual según el día de la semana. Las páginas diarias tienen día y mes, sin imponer un año. Usa febrero 29 solo cuando corresponda.'),
            ('Qué es manual','El agua, los hábitos y el avance de metas se registran a mano. El PDF no calcula rachas, no envía avisos, no sincroniza con la app y no actualiza los campos de otras páginas automáticamente.'),
            ('Cuida tu información','El archivo no está cifrado ni tiene contraseña. Guárdalo en un lugar privado y haz copias. Si anotas con lápiz, esas marcas pertenecen al PDF y no se importan a la aplicación Android.')
        ])
        y=623
        for label,body in blocks:
            text(label,184,y,17,True,color=ACC);y=wrap(body,184,y-28,790,12,19)-35
        button('Mi perfil','settings',184,67,150,34)
        button('Más información' if data==0 else 'Volver al inicio','guide-2' if data==0 else 'home',350,67,194,34)
    elif kind in ['month-index','day-index']:
        for m,label in enumerate(MONTHS,1):
            x=184+((m-1)%3)*274;y=512-((m-1)//3)*126
            box(x,y,258,108)
            text(label,x+18,y+72,19,True)
            button('Abrir mes',f'm{m:02}',x+18,y+20,110,32)
            button('Día 1',f'd{m:02}-01',x+140,y+20,100,32)
        text('Cada mes incluye enlaces a todos sus días.',184,71,11,color=MUTED)
    elif kind=='week-index':
        for m,label in enumerate(MONTHS,1):
            y=620-(m-1)*46;text(label,184,y+6,12,True)
            for w in range(1,7):button(f'{w}',f'w{m:02}-{w}',340+(w-1)*106,y-3,94,29)
        text('Seis plantillas por mes. Escribe las fechas y usa las que necesites.',184,52,11,color=MUTED)
    elif kind=='month':
        month_nav(m)
        field(f'{dest}-year','Año',184,577,98,27,maxlen=4)
        field(f'{dest}-focus','Este mes quiero dar espacio a...',304,577,686,27,maxlen=120)
        for col,label in enumerate(['Lunes','Martes','Miércoles','Jueves','Viernes','Sábado','Domingo']):text(label,190+col*115,550,10,True,color=ACC)
        for row in range(6):
            for col in range(7):field(f'{dest}-cell-{row}-{col}','',184+col*115,216+(5-row)*53,109,49,True,size=9,maxlen=120)
        text('Escribe las fechas en la cuadrícula. Abre tu página diaria:',184,191,11,True)
        for d in range(1,calendar.monthrange(2028,m)[1]+1):button(str(d),f'd{m:02}-{d:02}',184+((d-1)%16)*50,146-((d-1)//16)*36,44,29)
        text('Semanas del mes',184,79,10,True,color=MUTED)
        for w in range(1,7):button(str(w),f'w{m:02}-{w}',340+(w-1)*55,65,47,29)
    elif kind=='week':
        field(f'{dest}-period','Del / al',184,597,320,35,maxlen=70)
        field(f'{dest}-focus','Mi prioridad de la semana',530,597,460,35,maxlen=100)
        for j,label in enumerate(['Lunes','Martes','Miércoles','Jueves','Viernes','Sábado','Domingo','Notas y espacio para mí']):
            col=j%4;row=j//4;x=184+col*204;y=85+(1-row)*230
            field(f'{dest}-day-{j}',f'{label}: fecha y planes',x,y,192,184,True,size=11)
    elif kind=='day':
        m,d=data
        if m==2 and d==29:text('Día opcional: úsalo en años bisiestos.',184,647,10,color=ACC)
        else:button('Volver al mes',f'm{m:02}',184,630,142,28)
        field(f'{dest}-year','Año / día de la semana',720,616,270,28,maxlen=40)
        field(f'{dest}-intention','Hoy quiero sentirme / mi intención',184,548,806,39,maxlen=120)
        field(f'{dest}-plans','Mis planes y horarios',184,298,392,211,True,size=12)
        field(f'{dest}-priorities','Lo importante: mis prioridades',598,298,392,211,True,size=12)
        box(184,174,806,99,LIGHT)
        text('Mi autocuidado',200,247,14,True,color=ACC)
        check(f'{dest}-am','Skincare mañana',200,207)
        check(f'{dest}-pm','Skincare noche',386,207)
        field(f'{dest}-water','Agua tomada',598,198,124,29,maxlen=20)
        field(f'{dest}-watergoal','Mi meta / unidad',742,198,230,29,maxlen=30)
        field(f'{dest}-mood','Mi estado de ánimo',184,91,230,46,maxlen=80)
        field(f'{dest}-reflection','Reflexión / algo que agradezco',436,63,554,74,True,size=11)
    elif kind=='section-index':section_index(data)
    elif kind=='task':
        field(f'{dest}-name','Mi lista / periodo',184,597,806,35,maxlen=100)
        text('Una cosa a la vez. Marca lo que ya completaste.',184,568,12,color=MUTED)
        for j in range(10):
            y=509-j*43;check(f'{dest}-done-{j}',f'{j+1:02}',184,y+7)
            field(f'{dest}-task-{j}','',248,y,742,34,size=12,maxlen=160)
    elif kind in ['note','book']:
        field(f'{dest}-title','Título de mi página' if kind=='note' else 'Nombre del cuaderno / tema',184,597,806,35,maxlen=100)
        text('Espacio libre: usa la herramienta de lápiz de tu lector PDF.',184,565,11,color=MUTED)
        box(184,238,806,307);lines(184,238,806,307)
        field(f'{dest}-text','Texto con teclado / descripción de mi página',184,62,806,145,True,size=12,maxlen=6000)
    elif kind=='reflection':
        field(f'{dest}-date','Fecha',184,598,220,34,maxlen=40)
        field(f'{dest}-mood','Hoy me siento...',428,598,562,34,maxlen=100)
        field(f'{dest}-story','¿Cómo estuvo mi día?',184,357,806,202,True,size=12,maxlen=6000)
        field(f'{dest}-gratitude','Algo que agradezco',184,199,392,119,True,size=12)
        field(f'{dest}-care','Lo que necesito / cómo me cuidé',598,199,392,119,True,size=12)
        field(f'{dest}-tomorrow','Algo amable que me digo para mañana',184,62,806,99,True,size=12)
    elif kind=='habit':
        field(f'{dest}-period','Mes / periodo',184,599,350,34,maxlen=70)
        text('No necesitas hacerlo perfecto para seguir avanzando.',184,564,12,color=MUTED)
        for j in range(5):
            y=476-j*79
            field(f'{dest}-habit-{j}',f'Hábito {j+1}',184,y+15,190,30,maxlen=60)
            for d in range(1,32):
                x=392+(d-1)*19
                text(str(d),x+1,y+37,7,color=MUTED)
                fields.append(f'{dest}-h{j}-d{d}')
                c.acroForm.checkbox(name=fields[-1],tooltip=f'Hábito {j+1}, día {d}',x=x,y=y+13,size=16,checked=False,buttonStyle='check',borderColor=ACC,fillColor=white,textColor=ACC,forceBorder=True)
        field(f'{dest}-reflection','¿Qué me ayudó? ¿Qué quiero ajustar?',184,62,806,80,True,size=12)
    elif kind=='goal':
        field(f'{dest}-goal','Mi meta',184,582,806,50,maxlen=160,size=15)
        field(f'{dest}-why','¿Por qué es importante para mí?',184,432,806,110,True,size=12)
        field(f'{dest}-steps','Pasos concretos',184,215,502,177,True,size=12)
        field(f'{dest}-date','Fecha objetivo',708,344,282,48,maxlen=60)
        field(f'{dest}-progress','Mi avance (registro manual)',708,248,282,56,maxlen=80)
        check(f'{dest}-done','Meta alcanzada',708,215)
        field(f'{dest}-review','Mi siguiente paso / lo que aprendí',184,62,806,115,True,size=12)
    elif kind=='settings':
        field('settings-period','Periodo / año',184,588,390,44,maxlen=60)
        field('settings-water','Mi meta de agua y unidad personal',598,588,392,44,maxlen=100)
        field('settings-ritual','Mis rutinas de autocuidado',184,404,806,141,True,size=12)
        field('settings-backup','Mi plan de copias: dónde guardo el PDF y cuándo lo respaldo',184,237,806,125,True,size=12)
        wrap('Los nombres de la portada y estos campos son personales. El PDF no propaga valores ni modifica otras páginas automáticamente. Las metas de agua y el progreso se registran manualmente.',184,202,790,12,19)
        button('Ver guía de uso','guide',184,78,190,38)
    c.showPage()
c.save()
(TMP/'manifest.json').write_text(json.dumps({'pdf':str(PDF),'pages':len(plan),'fields':len(fields),'links':len(links),'destinations':[x[0] for x in plan],'plan':plan},ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'pdf':str(PDF),'pages':len(plan),'fields':len(fields),'links':len(links),'bytes':PDF.stat().st_size}))
