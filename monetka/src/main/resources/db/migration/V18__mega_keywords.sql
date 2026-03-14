-- ================================================================
-- V18: Mega keywords expansion + category fixes
-- ================================================================

-- 1. Удаляем "газ" из Авто/Топливо (газ = коммуналка, не бензин)
DELETE FROM subcategory_keywords
WHERE keyword IN ('газ','газу')
  AND subcategory_id = (SELECT id FROM subcategories WHERE name = 'Топливо');

-- 2. Удаляем неправильные слова из Аренда
DELETE FROM subcategory_keywords
WHERE keyword IN ('налог','газ','коммунальные','жкх','квитанция','счёт','свет','электро')
  AND subcategory_id = (SELECT id FROM subcategories WHERE name = 'Аренда');

-- ================================================================
-- 3. Новая подкатегория: Коммуналка (Дом)
-- ================================================================
INSERT INTO subcategories (name, emoji, category_id)
SELECT 'Коммуналка', '💡', id FROM categories WHERE name = 'Дом'
    ON CONFLICT DO NOTHING;

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT s.id, t.kw FROM subcategories s, (VALUES
                                             ('свет'),('электричество'),('электроэнергия'),('электро'),('счетчик'),
                                             ('квитанция за свет'),('оплата за свет'),('оплата света'),('электросети'),
                                             ('газ'),('газу'),('оплата газа'),('оплата за газ'),('газоснабжение'),
                                             ('вода'),('водоснабжение'),('водоотведение'),('канализация'),('водоканал'),
                                             ('оплата воды'),('оплата за воду'),
                                             ('отопление'),('теплоснабжение'),('тепло'),('батарея'),('теплосеть'),
                                             ('коммуналка'),('коммунальные'),('жкх'),('жку'),('квитанция'),('платежка'),
                                             ('управляющая компания'),('тсж'),('домком'),('вывоз мусора'),('мусор'),
                                             ('связь'),('мобильная связь'),('баланс телефона'),('баланс'),
                                             ('пополнение баланса'),('пополнить баланс'),('тариф')
) AS t(kw) WHERE s.name = 'Коммуналка'
    ON CONFLICT DO NOTHING;

-- ================================================================
-- 4. Новая подкатегория: Интернет (Дом)
-- ================================================================
INSERT INTO subcategories (name, emoji, category_id)
SELECT 'Интернет', '🌐', id FROM categories WHERE name = 'Дом'
    ON CONFLICT DO NOTHING;

DELETE FROM subcategory_keywords
WHERE keyword IN ('интернет','wi-fi','wifi','роутер','провайдер')
  AND subcategory_id = (SELECT id FROM subcategories WHERE name = 'Аренда');

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT s.id, t.kw FROM subcategories s, (VALUES
                                             ('интернет'),('wi-fi'),('wifi'),('вайфай'),('роутер'),('провайдер'),
                                             ('домашний интернет'),('оплата за интернет'),('оплата интернета'),
                                             ('оптоволокно'),('мегафон'),('билайн'),('мтс'),('теле2'),
                                             ('нур телеком'),('кыргызтелеком'),('интернет дома'),('тарифный план'),
                                             ('beeline'),('megafon')
) AS t(kw) WHERE s.name = 'Интернет'
    ON CONFLICT DO NOTHING;

-- ================================================================
-- 5. Аренда — только жильё
-- ================================================================
INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT s.id, t.kw FROM subcategories s, (VALUES
                                             ('аренда'),('аренду'),('квартира'),('квартиру'),('квартплата'),
                                             ('снимаю квартиру'),('снял квартиру'),('платим за квартиру'),('съём'),
                                             ('хозяин'),('хозяйке'),('за комнату'),('комнату'),
                                             ('дом снимаю'),('помещение'),('аренда жилья'),('rent'),('ипотека'),('ипотеку'),
                                             ('оплата аренды'),('оплата за аренду'),('оплата за квартиру'),('платим за жильё')
) AS t(kw) WHERE s.name = 'Аренда'
    ON CONFLICT DO NOTHING;

-- ================================================================
-- 6. Подписки — новые подкатегории
-- ================================================================
INSERT INTO subcategories (name, emoji, category_id)
SELECT 'Мобильная связь', '📞', id FROM categories WHERE name = 'Подписки'
    ON CONFLICT DO NOTHING;

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT s.id, t.kw FROM subcategories s, (VALUES
                                             ('мтс'),('билайн'),('мегафон'),('теле2'),('beeline'),('megafon'),
                                             ('мобильный'),('sim'),('сим'),('тариф телефон'),
                                             ('пополнить телефон'),('баланс телефона'),('мобильная связь'),
                                             ('оплата телефона'),('оплата мобильного'),('оплата за телефон'),
                                             ('пополнение мобильного'),('оплата связи')
) AS t(kw) WHERE s.name = 'Мобильная связь'
    ON CONFLICT DO NOTHING;

INSERT INTO subcategories (name, emoji, category_id)
SELECT 'Стриминг', '🎬', id FROM categories WHERE name = 'Подписки'
    ON CONFLICT DO NOTHING;

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT s.id, t.kw FROM subcategories s, (VALUES
                                             ('netflix'),('нетфликс'),('кинопоиск'),('иви'),('okko'),('ivi'),
                                             ('youtube premium'),('ютуб премиум'),('spotify'),('спотифай'),
                                             ('apple music'),('яндекс музыка'),('яндекс плюс'),
                                             ('disney'),('hbo'),('premier'),('амедиатека'),
                                             ('подписка на кино'),('стриминг')
) AS t(kw) WHERE s.name = 'Стриминг'
    ON CONFLICT DO NOTHING;

INSERT INTO subcategories (name, emoji, category_id)
SELECT 'Облако и сервисы', '☁', id FROM categories WHERE name = 'Подписки'
    ON CONFLICT DO NOTHING;

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT s.id, t.kw FROM subcategories s, (VALUES
                                             ('icloud'),('айклауд'),('google one'),('dropbox'),
                                             ('яндекс диск'),('onedrive'),('антивирус'),('vpn'),
                                             ('adobe'),('figma'),('notion'),('chatgpt'),('openai'),
                                             ('microsoft 365'),('подписка на сервис')
) AS t(kw) WHERE s.name = 'Облако и сервисы'
    ON CONFLICT DO NOTHING;

-- ================================================================
-- 7. Еда — расширение
-- ================================================================
INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT s.id, t.kw FROM subcategories s, (VALUES
                                             ('шаурмичная'),('лепёшка'),('самсушная'),('беляш'),('чебурек'),
                                             ('пирожки'),('беляши'),('тандыр'),('тандырная'),
                                             ('кебаб'),('шашлычная'),('гриль'),('уличная еда'),('еда на вынос'),
                                             ('дымсарай'),('дым сарай')
) AS t(kw) WHERE s.name = 'Фастфуд'
    ON CONFLICT DO NOTHING;

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT s.id, t.kw FROM subcategories s, (VALUES
                                             ('кофейня'),('кофе с собой'),('капучинка'),('флэт уайт'),
                                             ('раф'),('глясе'),('какао'),('тирамису'),('круассан'),
                                             ('сэндвич'),('бизнес ланч'),('ланч'),('чизкейк'),('блины'),('панкейк'),
                                             ('завтрак в кафе')
) AS t(kw) WHERE s.name = 'Кафе'
    ON CONFLICT DO NOTHING;

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT s.id, t.kw FROM subcategories s, (VALUES
                                             ('глово'),('glovo'),('яндекс еда'),('доставка еды'),
                                             ('вок'),('рамен'),('хачапури'),('стейк'),('пиццерия'),
                                             ('суши ресторан'),('японский ресторан')
) AS t(kw) WHERE s.name = 'Рестораны'
    ON CONFLICT DO NOTHING;

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT s.id, t.kw FROM subcategories s, (VALUES
                                             ('бакалея'),('рынок'),('базар'),('дордой'),('маселко'),('беталь'),
                                             ('народный'),('фреш'),('фреш маркет'),('шорпо'),('мясная лавка'),
                                             ('кефир'),('творог'),('сметана'),('сыр'),('масло'),('яйцо'),
                                             ('курица'),('говядина'),('баранина'),('рыба'),('колбаса'),
                                             ('сосиски'),('пельмени'),('мука'),('сахар'),('соль'),('специи'),
                                             ('хлебозавод'),('булочная'),('батон'),('выпечка'),
                                             ('детское питание'),('памперсы'),('подгузники')
) AS t(kw) WHERE s.name = 'Продукты'
    ON CONFLICT DO NOTHING;

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT s.id, t.kw FROM subcategories s, (VALUES
                                             ('кириешки'),('чипсы'),('попкорн'),('орешки'),('семечки'),
                                             ('сухарики'),('нутелла'),('халва'),('пахлава'),
                                             ('зефир'),('мармелад'),('шоколадка'),('батончик'),
                                             ('mars'),('snickers'),('twix'),('kitkat'),
                                             ('чупачупс'),('чупапус'),('леденец'),('карамель'),('жвачка')
) AS t(kw) WHERE s.name = 'Сладости'
    ON CONFLICT DO NOTHING;

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT s.id, t.kw FROM subcategories s, (VALUES
                                             ('айран'),('кумыс'),('шоро'),('максым'),('чалап'),
                                             ('минералка'),('coca-cola'),('пепси'),('fanta'),('sprite'),
                                             ('ред булл'),('monster'),('энергетик'),
                                             ('пиво'),('вино'),('шампанское'),('коктейль'),('алкоголь'),
                                             ('водка'),('коньяк'),('виски'),
                                             ('зеленый чай'),('черный чай'),('травяной чай')
) AS t(kw) WHERE s.name = 'Напитки'
    ON CONFLICT DO NOTHING;

-- ================================================================
-- 8. Транспорт — расширение
-- ================================================================
INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT s.id, t.kw FROM subcategories s, (VALUES
                                             ('маршрутное такси'),('пазик'),('проездной'),('карта проездная'),
                                             ('безлимитный проезд'),('проездной месяц')
) AS t(kw) WHERE s.name = 'Общественный транспорт'
    ON CONFLICT DO NOTHING;

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT s.id, t.kw FROM subcategories s, (VALUES
                                             ('яндего'),('yango'),('maxim'),('максим такси'),
                                             ('индрайвер'),('indrive'),('трансфер'),('аэропорт такси'),
                                             ('заказал такси'),('такси домой'),('поездка такси')
) AS t(kw) WHERE s.name = 'Такси'
    ON CONFLICT DO NOTHING;

-- ================================================================
-- 9. Авто — расширение
-- ================================================================
INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT s.id, t.kw FROM subcategories s, (VALUES
                                             ('бензин'),('заправился'),('заправка'),('азс'),('бенз'),
                                             ('92 бензин'),('95 бензин'),('дизель топливо'),('лукойл'),
                                             ('топливо авто'),('нефтепродукт')
) AS t(kw) WHERE s.name = 'Топливо'
    ON CONFLICT DO NOTHING;

INSERT INTO subcategories (name, emoji, category_id)
SELECT 'Страховка', '📋', id FROM categories WHERE name = 'Авто'
    ON CONFLICT DO NOTHING;

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT s.id, t.kw FROM subcategories s, (VALUES
                                             ('страховка'),('осаго'),('каско'),('страхование авто'),('страховой полис'),
                                             ('штраф'),('техосмотр'),('то автомобиля'),('технический осмотр')
) AS t(kw) WHERE s.name = 'Страховка'
    ON CONFLICT DO NOTHING;

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT s.id, t.kw FROM subcategories s, (VALUES
                                             ('запчасти'),('запчасть'),('автозапчасти'),('масло моторное'),
                                             ('фильтр масляный'),('аккумулятор авто'),('шины'),
                                             ('резина авто'),('диски колеса'),('свечи зажигания'),('колодки'),
                                             ('антифриз'),('тосол'),('автохимия')
) AS t(kw) WHERE s.name = 'Запчасти'
    ON CONFLICT DO NOTHING;

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT s.id, t.kw FROM subcategories s, (VALUES
                                             ('сто'),('автосервис'),('ремонт авто'),('шиномонтаж'),
                                             ('развал схождение'),('диагностика авто'),('автослесарь')
) AS t(kw) WHERE s.name = 'Ремонт'
             AND s.category_id = (SELECT id FROM categories WHERE name = 'Авто')
    ON CONFLICT DO NOTHING;

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT s.id, t.kw FROM subcategories s, (VALUES
                                             ('мойка'),('автомойка'),('мойку'),('помыл машину'),('химчистка салона'),
                                             ('ручная мойка'),('полировка'),('воск авто')
) AS t(kw) WHERE s.name = 'Мойка'
    ON CONFLICT DO NOTHING;

-- ================================================================
-- 10. Дом — расширение
-- ================================================================
INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT s.id, t.kw FROM subcategories s, (VALUES
                                             ('мебель'),('диван'),('кровать'),('шкаф'),('стол'),('стул'),('полка'),
                                             ('комод'),('матрас'),('подушка'),('одеяло'),('икеа'),('ikea'),
                                             ('сборка мебели'),('установка мебели'),('установка шкафа'),
                                             ('установка кондиционера'),('установка стиралки'),('монтаж мебели'),
                                             ('доставка мебели')
) AS t(kw) WHERE s.name = 'Мебель'
    ON CONFLICT DO NOTHING;

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT s.id, t.kw FROM subcategories s, (VALUES
                                             ('ремонт'),('строительные материалы'),('стройматериалы'),('цемент'),
                                             ('кирпич'),('плитка'),('ламинат'),('линолеум'),('обои'),('краска стены'),
                                             ('шпаклёвка'),('штукатурка'),('гипсокартон'),('грунтовка'),
                                             ('клей плиточный'),('сантехника'),('трубы'),('кран водопровод'),
                                             ('смеситель'),('унитаз'),('ванна'),('душевая кабина'),
                                             ('плиточник'),('маляр'),('штукатур'),('сварщик'),
                                             ('натяжной потолок'),('окна пвх'),('пластиковые окна'),('двери'),
                                             ('установка дверей'),('леруа'),('оби')
) AS t(kw) WHERE s.name = 'Ремонт'
             AND s.category_id = (SELECT id FROM categories WHERE name = 'Дом')
    ON CONFLICT DO NOTHING;

-- ================================================================
-- 11. Здоровье — расширение
-- ================================================================
INSERT INTO subcategories (name, emoji, category_id)
SELECT 'Стоматология', '🦷', id FROM categories WHERE name = 'Здоровье'
    ON CONFLICT DO NOTHING;

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT s.id, t.kw FROM subcategories s, (VALUES
                                             ('стоматолог'),('зубной'),('зубной врач'),('зубы'),('пломба'),
                                             ('удаление зуба'),('протезирование'),('брекеты'),('ортодонт'),
                                             ('чистка зубов'),('отбеливание зубов'),('дантист'),('стоматология')
) AS t(kw) WHERE s.name = 'Стоматология'
    ON CONFLICT DO NOTHING;

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT s.id, t.kw FROM subcategories s, (VALUES
                                             ('таблетки'),('лекарство'),('лекарства'),('препарат'),('витамины'),
                                             ('маска медицинская'),('термометр'),('тонометр'),('бинт'),('пластырь'),
                                             ('йод'),('мазь'),('капли'),('сироп'),('антибиотик'),
                                             ('парацетамол'),('ибупрофен'),('но-шпа'),('аспирин'),
                                             ('омега'),('рыбий жир'),('пробиотик'),('активированный уголь'),
                                             ('нурофен'),('цитрамон'),('смекта')
) AS t(kw) WHERE s.name = 'Аптека'
    ON CONFLICT DO NOTHING;

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT s.id, t.kw FROM subcategories s, (VALUES
                                             ('анализы'),('анализ крови'),('узи'),('мрт'),('кт'),('рентген'),
                                             ('флюорография'),('кардиограмма'),('консультация врача'),
                                             ('терапевт'),('педиатр'),('гинеколог'),('невролог'),('окулист'),('лор'),
                                             ('хирург'),('дерматолог'),('кардиолог'),('психолог'),
                                             ('поликлиника'),('клиника'),('больница'),('медцентр'),
                                             ('приём врача'),('визит к врачу'),('медосмотр')
) AS t(kw) WHERE s.name = 'Врачи'
    ON CONFLICT DO NOTHING;

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT s.id, t.kw FROM subcategories s, (VALUES
                                             ('фитнес'),('тренажёрный зал'),('тренажерный'),('качалка'),('спортзал'),
                                             ('абонемент зал'),('абонемент фитнес'),('фитнес-клуб'),
                                             ('йога'),('пилатес'),('растяжка'),('зумба'),('аэробика'),
                                             ('групповые занятия'),('персональный тренер'),('тренер')
) AS t(kw) WHERE s.name = 'Фитнес'
    ON CONFLICT DO NOTHING;

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT s.id, t.kw FROM subcategories s, (VALUES
                                             ('бокс'),('борьба'),('дзюдо'),('карате'),('кикбоксинг'),('мма'),
                                             ('волейбол'),('баскетбол'),('футбол'),('теннис'),('бадминтон'),
                                             ('тренировка'),('workout'),('кроссфит'),('абонемент спорт'),
                                             ('боксерские перчатки'),('боксерский мешок'),('татами'),
                                             ('спортивный инвентарь'),('гантели'),('штанга'),('скакалка')
) AS t(kw) WHERE s.name = 'Спорт'
    ON CONFLICT DO NOTHING;

-- ================================================================
-- 12. Покупки — новые подкатегории и расширение
-- ================================================================
INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT s.id, t.kw FROM subcategories s, (VALUES
                                             ('одежда'),('футболка'),('джинсы'),('штаны'),('брюки'),('платье'),
                                             ('куртка'),('пуховик'),('шапка'),('шарф'),('носки'),('белье'),('пижама'),
                                             ('кроссовки'),('туфли'),('ботинки'),('сапоги'),('обувь'),('кеды'),('сандалии'),
                                             ('zara'),('adidas'),('nike'),('puma'),('reebok'),
                                             ('сумка'),('рюкзак'),('кошелёк'),('чемодан'),
                                             ('секонд хенд'),('одежда онлайн')
) AS t(kw) WHERE s.name = 'Одежда'
    ON CONFLICT DO NOTHING;

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT s.id, t.kw FROM subcategories s, (VALUES
                                             ('телефон'),('смартфон'),('айфон'),('iphone'),('samsung'),('xiaomi'),
                                             ('планшет'),('ноутбук'),('компьютер'),('монитор'),('принтер'),
                                             ('телевизор'),('наушники'),('колонка'),('bluetooth колонка'),('airpods'),
                                             ('клавиатура'),('мышь компьютер'),('зарядка'),('кабель'),('power bank'),
                                             ('микроволновка'),('чайник электрический'),('блендер'),('мультиварка'),
                                             ('кофемашина'),('утюг'),('фен'),('стиральная машина'),('холодильник'),
                                             ('кондиционер'),('пылесос'),('обогреватель'),('вентилятор'),
                                             ('лампа'),('лампочка'),('светильник'),('люстра'),
                                             ('apple'),('huawei'),('oppo'),('xiaomi')
) AS t(kw) WHERE s.name = 'Техника'
    ON CONFLICT DO NOTHING;

INSERT INTO subcategories (name, emoji, category_id)
SELECT 'Красота', '💄', id FROM categories WHERE name = 'Покупки'
    ON CONFLICT DO NOTHING;

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT s.id, t.kw FROM subcategories s, (VALUES
                                             ('парикмахер'),('парикмахерская'),('стрижка'),('окрашивание'),('покраска волос'),
                                             ('укладка'),('кератин'),('маникюр'),('педикюр'),
                                             ('ногти'),('наращивание ногтей'),('гель лак'),('нейл'),
                                             ('брови'),('оформление бровей'),('наращивание ресниц'),
                                             ('татуаж'),('пирсинг'),
                                             ('косметика'),('макияж'),('тональный крем'),('помада'),('тушь'),
                                             ('крем для лица'),('сыворотка'),('маска для лица'),('патчи'),
                                             ('шампунь'),('кондиционер для волос'),('маска для волос'),
                                             ('гель для душа'),('мыло'),('дезодорант'),('парфюм'),('духи'),
                                             ('зубная паста'),('зубная щётка'),
                                             ('депиляция'),('воск'),('шугаринг'),('эпиляция'),
                                             ('nivea'),('dove'),('garnier')
) AS t(kw) WHERE s.name = 'Красота'
    ON CONFLICT DO NOTHING;

INSERT INTO subcategories (name, emoji, category_id)
SELECT 'Маркетплейс', '🛒', id FROM categories WHERE name = 'Покупки'
    ON CONFLICT DO NOTHING;

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT s.id, t.kw FROM subcategories s, (VALUES
                                             ('wildberries'),('wb'),('ozon'),('озон'),('авито'),
                                             ('aliexpress'),('алиэкспресс'),('ali'),('amazon'),
                                             ('kaspi'),('каспи'),('lamoda'),('ламода'),
                                             ('интернет магазин'),('онлайн заказ'),('заказал онлайн'),
                                             ('доставка посылки'),('посылка'),('cdek'),('сдэк')
) AS t(kw) WHERE s.name = 'Маркетплейс'
    ON CONFLICT DO NOTHING;

-- ================================================================
-- 13. Развлечения — расширение
-- ================================================================
INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT s.id, t.kw FROM subcategories s, (VALUES
                                             ('кино'),('кинотеатр'),('фильм'),('сеанс'),('imax'),('3d кино'),
                                             ('кинопарк'),('билим'),('киноплекс')
) AS t(kw) WHERE s.name = 'Кино'
    ON CONFLICT DO NOTHING;

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT s.id, t.kw FROM subcategories s, (VALUES
                                             ('steam'),('стим'),('playstation'),('ps5'),('ps4'),('xbox'),('nintendo'),
                                             ('игра'),('игры'),('компьютерная игра'),('мобильная игра'),('донат'),
                                             ('внутриигровые покупки'),('скин'),
                                             ('valorant'),('cs go'),('minecraft'),('roblox'),
                                             ('pubg'),('fortnite'),('dota'),('genshin'),
                                             ('геймпад'),('джойстик'),('наушники игровые')
) AS t(kw) WHERE s.name = 'Игры'
    ON CONFLICT DO NOTHING;

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT s.id, t.kw FROM subcategories s, (VALUES
                                             ('бар'),('паб'),('таверна'),('лаундж'),('кальян'),('кальянная'),
                                             ('коктейль бар'),('крафтовое пиво'),('пивной бар'),
                                             ('боулинг'),('бильярд')
) AS t(kw) WHERE s.name = 'Бары'
    ON CONFLICT DO NOTHING;

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT s.id, t.kw FROM subcategories s, (VALUES
                                             ('ночной клуб'),('дискотека'),('вечеринка'),('корпоратив'),
                                             ('банкетный зал'),('банкет'),
                                             ('концерт'),('стендап'),('спектакль'),('театр'),('опера'),('балет'),
                                             ('цирк'),('выставка'),('музей'),('галерея'),
                                             ('квест'),('лазертаг'),('пейнтбол'),('vr')
) AS t(kw) WHERE s.name = 'Клубы'
    ON CONFLICT DO NOTHING;

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT s.id, t.kw FROM subcategories s, (VALUES
                                             ('путешествие'),('поездка'),('тур'),('туроператор'),('турагентство'),
                                             ('виза'),('загранпаспорт'),
                                             ('отель'),('гостиница'),('апартаменты'),('хостел'),('глэмпинг'),
                                             ('airbnb'),('booking'),('букинг'),('кемпинг'),
                                             ('страховка туристическая'),('медицинская страховка'),
                                             ('перелёт'),('авиабилет'),('аэропорт'),
                                             ('море'),('пляж'),('курорт'),('санаторий'),('экскурсия'),('гид')
) AS t(kw) WHERE s.name = 'Путешествия'
    ON CONFLICT DO NOTHING;

-- ================================================================
-- 14. Образование — расширение
-- ================================================================
INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT s.id, t.kw FROM subcategories s, (VALUES
                                             ('вебинар'),('онлайн курс'),('мастер класс'),('обучение'),('тренинг'),
                                             ('коучинг'),('коуч'),('ментор'),('наставник'),
                                             ('английский язык'),('английский'),('немецкий'),
                                             ('французский'),('испанский'),('китайский'),('корейский'),('японский'),
                                             ('языковая школа'),('skyeng'),('duolingo'),
                                             ('программирование'),('дизайн курс'),('маркетинг курс'),
                                             ('smm курс'),('финансы курс')
) AS t(kw) WHERE s.name = 'Курсы'
    ON CONFLICT DO NOTHING;

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT s.id, t.kw FROM subcategories s, (VALUES
                                             ('учебник'),('тетрадь'),('тетради'),('ручки'),('карандаши'),
                                             ('пенал'),('портфель'),('рюкзак школьный'),
                                             ('ноты'),('альбом для рисования'),('краски'),('кисти'),('скетчбук'),
                                             ('маркеры'),('фломастеры'),('канцтовары'),('канцелярия'),
                                             ('научная литература'),('справочник'),('словарь'),('атлас'),
                                             ('электронные книги'),('kindle'),('литрес')
) AS t(kw) WHERE s.name = 'Учебники'
    ON CONFLICT DO NOTHING;

-- ================================================================
-- 15. Прочее — новые подкатегории
-- ================================================================
INSERT INTO subcategories (name, emoji, category_id)
SELECT 'Переводы', '💳', id FROM categories WHERE name = 'Прочее'
    ON CONFLICT DO NOTHING;

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT s.id, t.kw FROM subcategories s, (VALUES
                                             ('перевод'),('перевели'),('переслал'),('отправил деньги'),('кинул деньги'),
                                             ('скинул деньги'),('mbank'),('мбанк'),('элкарт'),('elcard'),
                                             ('western union'),('мгновенный перевод')
) AS t(kw) WHERE s.name = 'Переводы'
    ON CONFLICT DO NOTHING;

INSERT INTO subcategories (name, emoji, category_id)
SELECT 'Налоги и сборы', '🏛', id FROM categories WHERE name = 'Прочее'
    ON CONFLICT DO NOTHING;

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT s.id, t.kw FROM subcategories s, (VALUES
                                             ('налог'),('налоги'),('налогу'),('ндфл'),('ндс'),
                                             ('налог на имущество'),('налог на авто'),('транспортный налог'),
                                             ('земельный налог'),('госпошлина'),('пошлина'),('штраф налоговая'),
                                             ('нотариус'),('нотариальные услуги'),('апостиль')
) AS t(kw) WHERE s.name = 'Налоги и сборы'
    ON CONFLICT DO NOTHING;

INSERT INTO subcategories (name, emoji, category_id)
SELECT 'Подарки', '🎁', id FROM categories WHERE name = 'Прочее'
    ON CONFLICT DO NOTHING;

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT s.id, t.kw FROM subcategories s, (VALUES
                                             ('подарок'),('подарки'),('подарил'),('подарок другу'),('подарок маме'),
                                             ('подарок на день рождения'),
                                             ('цветы'),('букет'),('розы'),('тюльпаны'),('цветочный'),
                                             ('сертификат'),('подарочный сертификат'),
                                             ('шарики'),('воздушные шары'),
                                             ('8 марта'),('23 февраля'),('новый год подарок')
) AS t(kw) WHERE s.name = 'Подарки'
    ON CONFLICT DO NOTHING;

INSERT INTO subcategories (name, emoji, category_id)
SELECT 'Животные', '🐾', id FROM categories WHERE name = 'Прочее'
    ON CONFLICT DO NOTHING;

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT s.id, t.kw FROM subcategories s, (VALUES
                                             ('ветеринар'),('ветклиника'),('ветаптека'),
                                             ('корм кошки'),('корм собаки'),('корм питомца'),
                                             ('корм для кошек'),('корм для собак'),
                                             ('royal canin'),('whiskas'),('pedigree'),
                                             ('наполнитель кошачий'),('поводок'),('ошейник'),
                                             ('груминг'),('стрижка собаки'),('стрижка кошки'),('зоосалон'),
                                             ('зоомагазин'),('зоотовары')
) AS t(kw) WHERE s.name = 'Животные'
    ON CONFLICT DO NOTHING;