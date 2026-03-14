-- V17: Category quality fixes
-- ================================================================

-- Fix 1: Remove бассейн from Баня, add to Спорт (it was wrong)
-- ================================================================
DELETE FROM subcategory_keywords
WHERE keyword = 'бассейн'
  AND subcategory_id = (SELECT id FROM subcategories WHERE name = 'Баня');

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('бассейн'),('бассейне'),('плавание'),('плавать'),('swimming')
) AS t(kw) WHERE subcategories.name = 'Спорт'
    ON CONFLICT DO NOTHING;

-- ================================================================
-- Fix 2: Rename Баня → «СПА и баня» (more accurate)
-- ================================================================
UPDATE subcategories SET name = 'СПА и баня', emoji = '🧖'
WHERE name = 'Баня';

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('баня'),('банька'),('сауна'),('парилка'),('хамам'),('spa'),('спа'),
                                       ('флоатинг'),('джакузи'),('хаммам'),('хаммама'),('веник'),('баней')
) AS t(kw) WHERE subcategories.name = 'СПА и баня'
    ON CONFLICT DO NOTHING;

-- ================================================================
-- Fix 3: Развлечения — добавить подкатегорию «Активный отдых»
-- ================================================================
INSERT INTO subcategories (name, emoji, category_id)
SELECT 'Активный отдых', '🏞', id FROM categories WHERE name = 'Развлечения'
    ON CONFLICT DO NOTHING;

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('парк'),('прогулка'),('прогуляться'),('природа'),('лес'),('горы'),
                                       ('поход'),('треккинг'),('пикник'),('велосипед'),('самокат'),('роллики'),
                                       ('коньки'),('каток'),('лыжи'),('серфинг'),('рафтинг'),('квадроцикл'),
                                       ('верховая езда'),('скалолазание'),('зипсайн'),('каньон'),('водопад'),
                                       ('отдых на природе'),('выезд'),('шашлык'),('мангал'),
                                       ('отдых в парке'),('активный')
) AS t(kw) WHERE subcategories.name = 'Активный отдых'
    ON CONFLICT DO NOTHING;

-- Расширяем Путешествия: гид, экскурсия, авиа
INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('гид'),('гиду'),('гида'),('экскурсия'),('экскурсовод'),
                                       ('авиабилет'),('авиабилеты'),('перелёт'),('аэропорт'),('трансфер'),
                                       ('тур в горах'),('горный тур'),('туры'),('тур пакет'),('путёвка'),
                                       ('путёвку'),('отель'),('хостел'),('airbnb')
) AS t(kw) WHERE subcategories.name = 'Путешествия'
    ON CONFLICT DO NOTHING;

-- Каток — переносим в Активный отдых
DELETE FROM subcategory_keywords
WHERE keyword IN ('каток', 'коньки')
  AND subcategory_id IN (SELECT id FROM subcategories WHERE name IN ('Кино', 'Клубы', 'Игры', 'СПА и баня', 'Бары'));

-- ================================================================
-- Fix 4: Добавить категорию ОБРАЗОВАНИЕ
-- ================================================================
INSERT INTO categories (name, emoji, is_default)
VALUES ('Образование', '📚', false)
    ON CONFLICT (name) DO NOTHING;

INSERT INTO subcategories (name, emoji, category_id)
SELECT n, e, (SELECT id FROM categories WHERE name = 'Образование')
FROM (VALUES
          ('Курсы',        '🎓'),
          ('Репетиторы',   '👨‍🏫'),
          ('Автошкола',    '🚘'),
          ('Учебники',     '📖'),
          ('Онлайн курсы', '💻')
     ) AS t(n, e)
    ON CONFLICT DO NOTHING;

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('курсы'),('курс'),('обучение'),('тренинг'),('семинар'),('вебинар'),
                                       ('лекция'),('мастер класс'),('мастер-класс'),('воркшоп'),('workshop'),
                                       ('учёба'),('учёбу'),('занятия'),('секция'),('кружок')
) AS t(kw) WHERE subcategories.name = 'Курсы'
    ON CONFLICT DO NOTHING;

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('репетитор'),('репетитору'),('репетитора'),('репетиторство'),
                                       ('учитель'),('преподаватель'),('урок'),('уроки'),('частные уроки'),
                                       ('оплата репетитору'),('оплата за репетиторство')
) AS t(kw) WHERE subcategories.name = 'Репетиторы'
    ON CONFLICT DO NOTHING;

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('автошкола'),('автошколу'),('автошколе'),('вождение'),
                                       ('инструктор вождения'),('права'),('автодром'),('пдд'),
                                       ('оплата за автошколу'),('обучение вождению')
) AS t(kw) WHERE subcategories.name = 'Автошкола'
    ON CONFLICT DO NOTHING;

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('учебник'),('учебники'),('книга'),('книги'),('тетрадь'),('канцелярия'),
                                       ('учебные материалы'),('пособие'),('методичка'),('учебное пособие')
) AS t(kw) WHERE subcategories.name = 'Учебники'
    ON CONFLICT DO NOTHING;

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('udemy'),('coursera'),('skillbox'),('skyeng'),('geekbrains'),
                                       ('онлайн курс'),('онлайн обучение'),('edtech'),('stepik'),
                                       ('яндекс практикум'),('нетология')
) AS t(kw) WHERE subcategories.name = 'Онлайн курсы'
    ON CONFLICT DO NOTHING;

-- ================================================================
-- Fix 5: Дополнить Спорт
-- ================================================================
INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('бокс'),('борьба'),('дзюдо'),('карате'),('кикбоксинг'),('мма'),('mma'),
                                       ('волейбол'),('баскетбол'),('футбол'),('теннис'),('бадминтон'),
                                       ('спортивная секция'),('тренировка'),('спортзал'),('зал'),
                                       ('workout'),('кроссфит'),('crossfit'),('абонемент спорт')
) AS t(kw) WHERE subcategories.name = 'Спорт'
    ON CONFLICT DO NOTHING;

-- ================================================================
-- Fix 6: Дом/Ремонт — «услуги», «сервис»
-- ================================================================
INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('ремонт'),('мастер'),('сантехник'),('электрик'),('уборка'),('химчистка'),
                                       ('монтаж'),('установка'),('настройка'),('сборка'),('укладка'),
                                       ('плиточник'),('маляр'),('штукатурка')
) AS t(kw) WHERE subcategories.name = 'Ремонт'
    ON CONFLICT DO NOTHING;