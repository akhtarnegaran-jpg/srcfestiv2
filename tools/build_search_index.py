import json, re, subprocess
from pathlib import Path

root=Path(__file__).resolve().parents[1]
pdfdir=root/'app/src/main/assets/pdfs'
out=root/'app/src/main/assets/search-index.json'
titles={
'lab':'آزمایشگاه علوم تجربی','literature':'ادبیات و علوم انسانی','energy':'انرژی‌های نوین',
'robotics':'رباتیک و هوش مصنوعی','biotech':'زیست‌فناوری','stem':'سلول‌های بنیادی و پزشکی بازساختی',
'nuclear':'علوم و فنون هسته‌ای','nano':'نانوفناوری','aerospace':'حمل‌ونقل پیشرفته، هوافضا و دریا',
'astronomy':'نجوم و فناوری‌های فضایی','herbal':'گیاهان دارویی و طب سنتی','coding':'کدنویسی'}
items=[]
for pdf in sorted(pdfdir.glob('*.pdf')):
    raw=subprocess.check_output(['pdftotext','-layout',str(pdf),'-'],stderr=subprocess.DEVNULL).decode('utf-8','ignore')
    pages=[]
    for i,text in enumerate(raw.split('\f'),1):
        text=text.replace('\u202b','').replace('\u202c','').replace('\u200f',' ').replace('\u200e',' ')
        text=re.sub(r'\s+',' ',text).strip()
        if text: pages.append({'page':i,'text':text})
    items.append({'id':pdf.stem,'title':titles[pdf.stem],'pages':pages})
out.write_text(json.dumps(items,ensure_ascii=False,separators=(',',':')),encoding='utf-8')
print(out, out.stat().st_size)
