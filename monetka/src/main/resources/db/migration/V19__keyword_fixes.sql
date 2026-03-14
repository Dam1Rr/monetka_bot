-- ================================================================
-- V19: Fix wrong keyword categorizations
-- ================================================================

-- 1. МАСЛО — убираем из Авто/Ремонт, добавляем правильно
-- ================================================================
DELETE FROM subcategory_keywords
WHERE keyword IN ('масло','масло моторное','масло растительное')
  AND subcategory_id IN (
    SELECT id FROM subcategories WHERE name IN ('Ремонт','Запчасти','Топливо')
);

-- Масло моторное — в Авто/Запчасти
INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT s.id, t.kw FROM subcategories s, (VALUES
                                             ('масло моторное'),('моторное масло'),('масло для двигателя'),
                                             ('синтетическое масло'),('полусинтетика масло'),('замена масла')
) AS t(kw) WHERE s.name = 'Запчасти'
    ON CONFLICT DO NOTHING;

-- Масло растительное и продуктовое — в Еда/Продукты
INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT s.id, t.kw FROM subcategories s, (VALUES
                                             ('масло подсолнечное'),('масло растительное'),('масло оливковое'),
                                             ('масло сливочное'),('масло кокосовое'),('масло для готовки'),
                                             ('подсолнечное масло'),('оливковое масло'),('сливочное масло')
) AS t(kw) WHERE s.name = 'Продукты'
    ON CONFLICT DO NOTHING;

-- 2. ДОСТАВКА — убираем общее слово из Ресторанов, делаем точнее
-- ================================================================
DELETE FROM subcategory_keywords
WHERE keyword IN ('доставка','delivery')
  AND subcategory_id IN (
    SELECT id FROM subcategories WHERE name IN ('Рестораны','Клубы','Путешествия')
);

-- Доставка еды — в Рестораны (только конкретные)
INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT s.id, t.kw FROM subcategories s, (VALUES
                                             ('доставка еды'),('доставка пиццы'),('доставка суши'),('заказ еды'),
                                             ('glovo'),('глово'),('яндекс еда'),('доставка из ресторана')
) AS t(kw) WHERE s.name = 'Рестораны'
    ON CONFLICT DO NOTHING;

-- Доставка груза/посылок — в Транспорт/Межгород
INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT s.id, t.kw FROM subcategories s, (VALUES
                                             ('доставка груза'),('доставка посылки'),('доставка товара'),
                                             ('доставка в другой город'),('доставка по городу'),
                                             ('курьерская доставка'),('курьер груз'),('курьерская служба'),
                                             ('cdek'),('сдэк'),('ems'),('почта доставка'),('dhl'),
                                             ('транспортная компания'),('грузоперевозка'),('грузоперевозки'),
                                             ('перевозка вещей'),('газель'),('доставка мебели груз'),
                                             ('отправка посылки'),('посылка почта')
) AS t(kw) WHERE s.name = 'Межгород'
    ON CONFLICT DO NOTHING;

-- 3. ТОНИРОВКА — убираем fuzzy match к "тренировка"
-- ================================================================
-- Тонировка — это Авто
INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT s.id, t.kw FROM subcategories s, (VALUES
                                             ('тонировка'),('тонировку'),('тонировки'),('тонирование стёкол'),
                                             ('тонирование окон'),('плёнка авто'),('бронеплёнка'),
                                             ('оклейка авто'),('оклейка плёнкой'),('антигравий'),
                                             ('виниловая плёнка'),('чип тюнинг'),('тюнинг авто')
) AS t(kw) WHERE s.name = 'Мойка'
    ON CONFLICT DO NOTHING;

-- 4. ШЕСТИГРАННИК и инструменты — Авто/Запчасти или Дом/Ремонт
-- ================================================================
INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT s.id, t.kw FROM subcategories s, (VALUES
                                             ('шестигранник'),('отвёртка'),('гаечный ключ'),('дрель'),('шуруповёрт'),
                                             ('перфоратор'),('болгарка'),('уровень строительный'),('рулетка'),
                                             ('молоток'),('пила'),('лобзик'),('стамеска'),('инструмент'),
                                             ('строительный инструмент'),('электроинструмент'),('набор инструментов'),
                                             ('саморезы'),('болты'),('гайки'),('шурупы'),('дюбели'),('гвозди')
) AS t(kw) WHERE s.name = 'Ремонт'
             AND s.category_id = (SELECT id FROM categories WHERE name = 'Дом')
    ON CONFLICT DO NOTHING;

-- 5. УСЛУГИ — правильная категоризация
-- ================================================================
INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT s.id, t.kw FROM subcategories s, (VALUES
                                             ('сантехник'),('услуги сантехника'),('вызов сантехника'),
                                             ('сантехнические работы'),('замена труб'),('замена крана'),
                                             ('засор'),('прочистка'),('унитаз ремонт'),('водопровод ремонт'),
                                             ('электрик'),('услуги электрика'),('вызов электрика'),
                                             ('проводка'),('розетка'),('выключатель'),('щиток'),
                                             ('уборка'),('клининг'),('генеральная уборка'),('уборщица'),
                                             ('химчистка ковра'),('химчистка дивана'),('мойка окон'),
                                             ('установка замка'),('замок'),('дверной замок'),('слесарь'),
                                             ('грузчики'),('переезд'),('помощь с переездом'),
                                             ('услуги мастера'),('услуги строителя'),('прораб'),
                                             ('строительные работы'),('ремонтные работы'),('отделочные работы')
) AS t(kw) WHERE s.name = 'Ремонт'
             AND s.category_id = (SELECT id FROM categories WHERE name = 'Дом')
    ON CONFLICT DO NOTHING;

-- 6. Автомобильные услуги (тонировка, мойка) — уточняем
-- ================================================================
INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT s.id, t.kw FROM subcategories s, (VALUES
                                             ('детейлинг'),('детейлинг авто'),('полировка кузова'),
                                             ('бронирование фар'),('ксенон'),('светодиодные фары'),
                                             ('шумоизоляция'),('антикор'),('антикоррозийная обработка'),
                                             ('кузовные работы'),('покраска бампера'),('рихтовка')
) AS t(kw) WHERE s.name = 'Мойка'
    ON CONFLICT DO NOTHING;

-- 7. КАТОК — убираем из неправильных мест, ставим в Активный отдых
-- ================================================================
DELETE FROM subcategory_keywords
WHERE keyword IN ('каток','коньки')
  AND subcategory_id IN (
    SELECT id FROM subcategories WHERE name IN ('Спорт','Фитнес')
);

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT s.id, t.kw FROM subcategories s, (VALUES
                                             ('каток'),('коньки'),('фигурное катание'),('хоккей любительский'),
                                             ('каток платный'),('прокат коньков'),('ледовый каток')
) AS t(kw) WHERE s.name = 'Активный отдых'
    ON CONFLICT DO NOTHING;

-- 8. Разное часто встречающееся
-- ================================================================
INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT s.id, t.kw FROM subcategories s, (VALUES
                                             ('аптека'),('аптечка'),('аптеке'),('в аптеке'),('купил в аптеке')
) AS t(kw) WHERE s.name = 'Аптека'
    ON CONFLICT DO NOTHING;

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT s.id, t.kw FROM subcategories s, (VALUES
                                             ('такси до аэропорта'),('uber'),('яндекс'),('yandex go'),('инд')
) AS t(kw) WHERE s.name = 'Такси'
    ON CONFLICT DO NOTHING;

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT s.id, t.kw FROM subcategories s, (VALUES
                                             ('оплата за секцию'),('секция бокса'),('секция футбола'),
                                             ('секция плавания'),('секция борьбы'),('секция карате'),
                                             ('спортивная секция'),('запись в секцию'),('платная секция')
) AS t(kw) WHERE s.name = 'Спорт'
    ON CONFLICT DO NOTHING;