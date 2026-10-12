#!/bin/bash
set -euo pipefail
if ! command -v python3 >/dev/null 2>&1; then
  echo 'Python 3 не знайдено. Встановіть Python 3 для macOS.' >&2
  exit 1
fi
python3 - <<'PYTHON'
import base64,datetime,getpass,json,os,re,sys,urllib.error,urllib.request

# Shell передає Python-код через heredoc, тому звичайний stdin уже вичерпано.
# Читаємо інтерактивні запити безпосередньо з термінала macOS.
try:
    sys.stdin = open("/dev/tty", "r", encoding="utf-8")
except OSError:
    raise SystemExit("ПОМИЛКА: потрібен інтерактивний Terminal (/dev/tty). Запустіть файл у Terminal, а не через pipe.")

BASE="https://studiumqa2026.testrail.io"
DATA=json.loads(r'''[[15,"Створення без обов’язкової назви","rules","High","Викладач авторизований; доступна форма нового завдання.","1. Відкрити форму створення.\n2. Залишити назву порожньою.\n3. Натиснути збереження.\n4. Перевірити список завдань.","Система відхиляє збереження без обов'язкової назви, пояснює причину, порожній запис не з'являється."],[16,"Редагування чернетки","rules","Medium","Викладач має власне завдання зі станом DRAFT.","1. Відкрити власну чернетку.\n2. Змінити назву й опис.\n3. Зберегти.\n4. Відкрити чернетку повторно.","Зміни зберігаються і відображаються після повторного відкриття; без публікації стан залишається DRAFT."],[17,"Редагування чужого завдання викладачем","rules","High","Створено завдання викладача А; викладач Б не має прав на його зміну.","1. Авторизуватися викладачем Б.\n2. Спробувати змінити завдання А через інтерфейс.\n3. Повторити запит змін через API.\n4. Перевірити стан завдання А.","Сервер відхиляє неавторизовану зміну; дані завдання зберігаються без змін."],[18,"Здача до і після дедлайну","rules","High","Завдання опубліковано, пізню здачу заборонено; студент має доступну спробу.","1. Підготувати дві незалежні здачі.\n2. Здати роботу незадовго до дедлайну.\n3. Спробувати здати іншу роботу після дедлайну.\n4. Зафіксувати час і результат.","До дедлайну робота приймається, після дедлайну відхиляється. Трактування точної секунди дедлайну уточнюється за бізнес-правилом."],[19,"Остання дозволена спроба","rules","High","Дозволено N здач; студент використав N−1 спроб; дедлайн чинний.","1. Відкрити завдання.\n2. Здати роботу в N-ту дозволену спробу.\n3. Перевірити результат і лічильник.","Останню дозволену спробу прийнято рівно один раз; лічильник не перевищує N."],[20,"Відхилення від’ємної оцінки","rules","High","Є здана робота й викладач із правом оцінювання.","1. Відкрити роботу.\n2. Ввести оцінку -1.\n3. Спробувати зберегти через UI та/або API.\n4. Перевірити дані оцінювання.","Від'ємна оцінка відхиляється; некоректне значення не зберігається."],[21,"Оцінка понад максимальну","rules","High","Є актуальна здана робота, її maxPoints відомий.","1. Відкрити роботу.\n2. Ввести maxPoints+1.\n3. Надіслати запит.\n4. Перевірити збережене значення.","Значення вище maxPoints відхиляється; раніше збережена оцінка не змінюється."],[22,"Заборона перегляду чужої здачі","rules","High","Студент А здав роботу; студент Б не має дозволу на її перегляд.","1. Увійти як студент Б.\n2. Спробувати відкрити здачу А за прямим посиланням.\n3. Повторити запит через API.\n4. Перевірити відсутність даних А.","Сервер відмовляє у доступі; файл, відгук та персональні дані студента А недоступні."],[23,"UI форми створення завдання","ui","Medium","Викладач відкрив сторінку створення завдання.","1. Перевірити заголовок, поля, підписи, кнопки.\n2. Заповнити поля.\n3. Перевірити, що важливі елементи не обрізані і не перекриваються.","Основні елементи форми доступні, підписані та коректно відображаються."],[24,"UI повідомлень валідації","ui","Medium","Відкрита форма створення завдання.","1. Відправити форму без обов'язкових даних.\n2. Перевірити повідомлення й індикацію помилки.\n3. Виправити дані та повторити дію.","Повідомлення зрозуміле й пов'язане з проблемним полем; після виправлення помилка зникає."],[25,"UI списку, дедлайнів і статусів","ui","Medium","Є тестові завдання з відомими статусами і дедлайнами.","1. Відкрити список завдань.\n2. Зіставити статуси й дедлайни з тестовими даними.\n3. Відкрити деталі й повернутися.\n4. Перевірити порожній список.","Назви, статуси й дедлайни відображаються коректно; навігація працює; порожній список має зрозумілий стан."],[26,"UI дій відповідно до ролі","ui","High","Існують студент і викладач із доступом до однієї дисципліни.","1. Відкрити завдання як викладач.\n2. Зафіксувати доступні дії.\n3. Відкрити те саме завдання як студент.\n4. Порівняти перелік дій.","Керувальні дії відповідають ролі; студент не бачить дій викладача. Серверні права перевіряються окремо."],[27,"Адаптивність сторінок завдань","ui","Medium","Доступні список, деталі й форма завдань у браузері.","1. Перевірити сторінки за ширини 375 px.\n2. Повторити за 768 px.\n3. Повторити за 1280 px.\n4. Оцінити кнопки, текст, скрол і меню.","На заявлених підтримуваних ширинах критичні елементи не перекриваються та залишаються доступними."],[28,"Кросбраузерність модуля завдань","ui","Medium","Є список підтримуваних браузерів і однакові тестові дані.","1. Перевірити список завдань у Chrome.\n2. Повторити у Firefox і Safari, якщо підтримуються.\n3. Перевірити деталі, форми й повідомлення.","Основні сценарії однаково працюють у заявлених підтримуваних браузерах; несумісності фіксуються як дефекти."],[29,"Клавіатурна доступність UI","ui","Medium","Відкрита сторінка завдань; основні дії доступні.","1. Переміщувати фокус Tab та Shift+Tab.\n2. Активувати кнопки Enter/Space.\n3. Перевірити видимість фокуса.\n4. Спробувати вийти з діалогу клавіатурою.","Логічна послідовність та видимість фокуса, можливість використання основних дій без миші."],[30,"Наскрізна інтеграція завдань і файлового сервісу","integration","High","Працюють gateway, assignment-service, file-service і сховище; є студент, викладач та завдання.","1. Завантажити дозволений файл як студент.\n2. Здати роботу з файлом.\n3. Відкрити цю здачу як викладач.\n4. Переглянути або завантажити файл.\n5. Зіставити вміст.","Завдання, здача й файл пов'язані; викладач отримує той самий файл, а доступ захищений."]]''')
GROUPS=json.loads(r'''[["rules","05. Extended Business Rules & Boundaries","New positive, negative and boundary test cases for assignments.","Additional Business Rules - Lototskyi","lot"],["ui","06. UI, Accessibility & Compatibility","Interface, responsiveness, supported browsers and keyboard accessibility.","UI & Compatibility - Semenenko","sem"],["integration","07. End-to-End Integration","Full assignment and file-service cross-component workflow.","End-to-End Integration - Semenenko","sem"]]''')
PROJECT_ID=1
PLAN_ID=1

def stop(message):
    raise SystemExit("\nПОМИЛКА: "+str(message))

print("Studium — TestRail macOS setup")
print("Проєкт P1, план R1. Жодні старі результати не змінюються.")
email=input("Email TestRail: ").strip()
if "@" not in email: stop("Некоректний email")
key=getpass.getpass("API key (приховано): ").strip()
if not key: stop("Не введено API key")
token=base64.b64encode((email+":"+key).encode()).decode()

def api(method,path,payload=None):
    data=None if payload is None else json.dumps(payload,ensure_ascii=False).encode("utf-8")
    headers={"Authorization":"Basic "+token,"Accept":"application/json"}
    if data is not None: headers["Content-Type"]="application/json"
    req=urllib.request.Request(BASE+"/index.php?/api/v2/"+path,data=data,headers=headers,method=method)
    try:
        with urllib.request.urlopen(req,timeout=45) as response:
            raw=response.read().decode("utf-8")
            return json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        body=e.read().decode("utf-8","replace")[:600]
        stop("API "+path.split("&")[0]+" відповів HTTP "+str(e.code)+": "+body)
    except Exception as e:
        stop("Немає зв'язку з TestRail: "+str(e))

def paginate(path,key):
    output=[]
    offset=0
    for _ in range(100):
        response=api("GET",path+"&limit=250&offset="+str(offset))
        if isinstance(response,list): return output+response
        if not isinstance(response,dict): stop("Невідомий формат "+path)
        chunk=response.get(key,[])
        if not isinstance(chunk,list): stop("Некоректні дані "+key)
        output+=chunk
        if not (response.get("_links") or {}).get("next") or not chunk: return output
        offset+=len(chunk)
    stop("Занадто багато сторінок "+path)

def number_from_title(title):
    match=re.match(r"^TC-(\d+)\b",str(title or ""))
    return int(match.group(1)) if match else None

print("\nПеревірка поточного стану через API...")
project=api("GET","get_project/1")
plan=api("GET","get_plan/1")
if project.get("name","").casefold()!="studium": stop("P1 — не Studium")
if plan.get("project_id")!=1 or "studium" not in plan.get("name","").casefold():
    stop("R1 — не очікуваний план Studium")
if plan.get("is_completed"): stop("План закрито, змінювати його не можна")
cases=paginate("get_cases/1","cases")
sections=paginate("get_sections/1","sections")
user_data=api("GET","get_users/1")
users=user_data if isinstance(user_data,list) else user_data.get("users",[])
prior_data=api("GET","get_priorities")
priorities=prior_data if isinstance(prior_data,list) else prior_data.get("priorities",[])
if not isinstance(users,list) or not users: stop("Список учасників порожній")

existing={}
for case in cases:
    n=number_from_title(case.get("title"))
    if n is not None:
        if n in existing: stop("Знайдено дублікати TC-%02d" % n)
        existing[n]=case
if any(i not in existing for i in range(1,15)):
    stop("Не знайдено весь базовий набір TC-01..TC-14")
sample=existing[1]
for field in ("custom_preconds","custom_steps","custom_expected"):
    if field not in sample: stop("Текстовий шаблон кейсів не містить "+field)
for spec in DATA:
    i,title=spec[:2]
    if i in existing and existing[i].get("title")!="TC-%02d — %s" % (i,title):
        stop("TC-%02d існує з іншою назвою; виправте конфлікт вручну" % i)

section_map={}
for sec in sections:
    name=sec.get("name")
    if name in section_map: stop("Є два розділи з назвою "+str(name))
    section_map[name]=sec
entry_map={}
for entry in plan.get("entries",[]):
    name=entry.get("name")
    if name in entry_map: stop("Є повторені записи плану "+str(name))
    entry_map[name]=entry

print("\nДоступні користувачі Studium:")
for u in users:
    print(" %s — %s (%s)" % (u.get("id"),u.get("name"),u.get("email","")))
def ask_user(label):
    value=input("Введіть ID "+label+": ").strip()
    if not value.isdigit(): stop("Потрібний числовий ID")
    val=int(value)
    if val not in {u.get("id") for u in users}: stop("Користувача не знайдено у P1")
    return val
lot=ask_user("Лотоцького")
sem=ask_user("Семененка")
if lot==sem: stop("Оберіть різні облікові записи")
assign={"lot":lot,"sem":sem}

print("\nПопередній перегляд:")
print(" Наявних кейсів у проєкті:",len(cases))
print(" Нових кейсів:",sum(spec[0] not in existing for spec in DATA))
for spec in DATA:
    i,title=spec[:2]
    print("  TC-%02d %s: %s" % (i,"[вже існує]" if i in existing else "[створити]",title))
for group,section,description,entry,who in GROUPS:
    print("  %s — %s" % (section,"вже є" if section in section_map else "створити"))
    print("  Run: %s — %s" % (entry,"вже є" if entry in entry_map else "створити"))
print(" TC-10 буде уточнено як позитивний сценарій; TC-20/21 — негативні.")
print(" Наявні прогони і призначення T1–T14 не зміняться.")
if input("\nДля внесення змін введіть APPLY (Enter = нічого не змінювати): ").strip()!="APPLY":
    print("Завершено без змін.")
    sys.exit(0)

filename="Studium_TestRail_backup_"+datetime.datetime.now().strftime("%Y%m%d_%H%M%S")+".json"
try:
    with open(filename,"x",encoding="utf-8") as out:
        json.dump({"project":project,"plan":plan,"cases":cases,"sections":sections},out,ensure_ascii=False,indent=2)
    os.chmod(filename,0o600)
except Exception as e:
    stop("Не вдалося створити резервну копію. Змін не внесено: "+str(e))
print("Збережено резервну копію:",filename)
priority_map={str(p.get("name","")).casefold():p.get("id") for p in priorities if isinstance(p,dict)}
group_map={g[0]:g for g in GROUPS}
for g,section,description,entry,who in GROUPS:
    if section not in section_map:
        section_map[section]=api("POST","add_section/1",{"name":section,"description":description})
        print("+ Section:",section)
for spec in DATA:
    i,title,group,priority,pre,steps,expected=spec
    if i in existing:
        print("= TC-%02d уже існує" % i)
        continue
    payload={"title":"TC-%02d — %s" % (i,title),"custom_preconds":pre,"custom_steps":steps,"custom_expected":expected}
    if priority.casefold() in priority_map:
        payload["priority_id"]=priority_map[priority.casefold()]
    if sample.get("template_id") is not None:
        payload["template_id"]=sample["template_id"]
    section_id=section_map[group_map[group][1]]["id"]
    created=api("POST","add_case/"+str(section_id),payload)
    existing[i]=created
    print("+ TC-%02d створено як C%s" % (i,created.get("id")))

fix10={
  "custom_preconds":"Викладач авторизований, є актуальна здача студента й визначено maxPoints.",
  "custom_steps":"1. Відкрити актуальну здачу.\n2. Установити допустиму оцінку в межах 0..maxPoints.\n3. Зберегти оцінку.\n4. Повторно переглянути результат як викладач і студент.",
  "custom_expected":"Допустима оцінка збережена й правильно відображається уповноваженим користувачам. Негативні випадки перевіряються в TC-20 і TC-21."
}
if any(existing[10].get(k)!=v for k,v in fix10.items()):
    api("POST","update_case/"+str(existing[10]["id"]),fix10)
    print("~ Уточнено TC-10")
suite_id=sample.get("suite_id")
for entry in plan.get("entries",[]):
    if entry.get("suite_id") is not None:
        suite_id=entry["suite_id"]
        break
for group,section,description,entry,who in GROUPS:
    case_ids=[existing[s[0]]["id"] for s in DATA if s[2]==group]
    if entry in entry_map:
        print("= У плані вже є:",entry)
        continue
    body={"name":entry,"description":description,"assignedto_id":assign[who],"include_all":False,"case_ids":case_ids}
    if suite_id is not None: body["suite_id"]=suite_id
    entry_map[entry]=api("POST","add_plan_entry/1",body)
    print("+ Run:",entry,"(",len(case_ids),"кейсів )")
new_cases=paginate("get_cases/1","cases")
numbers={number_from_title(c.get("title")) for c in new_cases}
if not all(i in numbers for i in range(1,31)):
    stop("Після оновлення не всі TC-01..TC-30 наявні")
new_plan=api("GET","get_plan/1")
entry_names={e.get("name") for e in new_plan.get("entries",[])}
if not all(g[3] in entry_names for g in GROUPS):
    stop("Після оновлення бракує запису тест-плану")
print("\nГОТОВО: TC-01..TC-30 існують, нові групи додано до R1.")
print("Нові кейси: Лотоцький +8, Семененко +8. Результати тестів не змінювалися.")
print("Відкрити: "+BASE+"/index.php?/plans/view/1")

PYTHON
