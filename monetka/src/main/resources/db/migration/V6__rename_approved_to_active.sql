-- ============================================================
-- V6: Rename status APPROVED → ACTIVE
--     + Expand categories / subcategories
-- ============================================================

-- 1. Rename existing APPROVED status to ACTIVE
UPDATE users SET status = 'ACTIVE' WHERE status = 'APPROVED';

-- ============================================================
-- 2. Add new top-level categories (only if not already present)
-- ============================================================
INSERT INTO categories (name, emoji, is_default) VALUES
                                                     ('Путешествия',   '✈️', false),
                                                     ('Одежда',        '👗', false),
                                                     ('Косметика',     '💄', false),
                                                     ('Электроника',   '💻', false),
                                                     ('Образование',   '📚', false),
                                                     ('Подарки',       '🎁', false),
                                                     ('Спорт',         '🏋️', false),
                                                     ('Животные',      '🐾', false),
                                                     ('Связь',         '📡', false),
                                                     ('Инвестиции',    '📈', false),
                                                     ('Бизнес',        '💼', false),
                                                     ('Налоги',        '🧾', false)
    ON CONFLICT (name) DO NOTHING;

-- ============================================================
-- 3. Add subcategories for new categories
-- ============================================================

-- Путешествия
INSERT INTO subcategories (name, emoji, category_id)
SELECT s.name, s.emoji, c.id
FROM categories c,
     (VALUES
          ('Перелёты',    '✈️'),
          ('Отели',       '🏨'),
          ('Визы',        '🛂'),
          ('Экскурсии',   '🗺️'),
          ('Страховка',   '🛡️')
     ) AS s(name, emoji)
WHERE c.name = 'Путешествия'
    ON CONFLICT DO NOTHING;

-- Одежда
INSERT INTO subcategories (name, emoji, category_id)
SELECT s.name, s.emoji, c.id
FROM categories c,
     (VALUES
          ('Куртки',      '🧥'),
          ('Футболки',    '👕'),
          ('Обувь',       '👟'),
          ('Аксессуары',  '👜'),
          ('Спортивная',  '🩱')
     ) AS s(name, emoji)
WHERE c.name = 'Одежда'
    ON CONFLICT DO NOTHING;

-- Косметика
INSERT INTO subcategories (name, emoji, category_id)
SELECT s.name, s.emoji, c.id
FROM categories c,
     (VALUES
          ('Уход за лицом',  '🧴'),
          ('Парфюмерия',     '🌸'),
          ('Макияж',         '💋'),
          ('Уход за волосами','💇')
     ) AS s(name, emoji)
WHERE c.name = 'Косметика'
    ON CONFLICT DO NOTHING;

-- Электроника
INSERT INTO subcategories (name, emoji, category_id)
SELECT s.name, s.emoji, c.id
FROM categories c,
     (VALUES
          ('Смартфоны',   '📱'),
          ('Ноутбуки',    '💻'),
          ('Аксессуары',  '🔌'),
          ('Игры',        '🎮'),
          ('Умный дом',   '🏠')
     ) AS s(name, emoji)
WHERE c.name = 'Электроника'
    ON CONFLICT DO NOTHING;

-- Образование
INSERT INTO subcategories (name, emoji, category_id)
SELECT s.name, s.emoji, c.id
FROM categories c,
     (VALUES
          ('Курсы',       '🖥️'),
          ('Книги',       '📖'),
          ('Репетитор',   '👨‍🏫'),
          ('Сертификаты', '🏅')
     ) AS s(name, emoji)
WHERE c.name = 'Образование'
    ON CONFLICT DO NOTHING;

-- Подарки
INSERT INTO subcategories (name, emoji, category_id)
SELECT s.name, s.emoji, c.id
FROM categories c,
     (VALUES
          ('День рождения',   '🎂'),
          ('Праздники',       '🎄'),
          ('Сувениры',        '🪆')
     ) AS s(name, emoji)
WHERE c.name = 'Подарки'
    ON CONFLICT DO NOTHING;

-- Спорт
INSERT INTO subcategories (name, emoji, category_id)
SELECT s.name, s.emoji, c.id
FROM categories c,
     (VALUES
          ('Абонемент',       '🏋️'),
          ('Инвентарь',       '⚽'),
          ('Соревнования',    '🏆'),
          ('Питание',         '🥤')
     ) AS s(name, emoji)
WHERE c.name = 'Спорт'
    ON CONFLICT DO NOTHING;

-- Животные
INSERT INTO subcategories (name, emoji, category_id)
SELECT s.name, s.emoji, c.id
FROM categories c,
     (VALUES
          ('Корм',            '🍗'),
          ('Ветеринар',       '🏥'),
          ('Аксессуары',      '🦮'),
          ('Груминг',         '✂️')
     ) AS s(name, emoji)
WHERE c.name = 'Животные'
    ON CONFLICT DO NOTHING;

-- Связь
INSERT INTO subcategories (name, emoji, category_id)
SELECT s.name, s.emoji, c.id
FROM categories c,
     (VALUES
          ('Мобильная связь', '📱'),
          ('Интернет',        '🌐'),
          ('ТВ',              '📺')
     ) AS s(name, emoji)
WHERE c.name = 'Связь'
    ON CONFLICT DO NOTHING;

-- Инвестиции
INSERT INTO subcategories (name, emoji, category_id)
SELECT s.name, s.emoji, c.id
FROM categories c,
     (VALUES
          ('Акции',           '📊'),
          ('Крипто',          '₿'),
          ('Депозит',         '🏦'),
          ('Недвижимость',    '🏠')
     ) AS s(name, emoji)
WHERE c.name = 'Инвестиции'
    ON CONFLICT DO NOTHING;

-- ============================================================
-- 4. Keywords for new subcategories
-- ============================================================

-- Одежда / Обувь
INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('кроссовки'),('ботинки'),('туфли'),('сапоги'),('слипоны'),('тапки'),('сандалии')
) AS t(kw) WHERE subcategories.name = 'Обувь'
    ON CONFLICT DO NOTHING;

-- Одежда / Куртки
INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('куртка'),('пальто'),('пуховик'),('ветровка'),('кардиган'),('пиджак'),('худи')
) AS t(kw) WHERE subcategories.name = 'Куртки'
    ON CONFLICT DO NOTHING;

-- Электроника / Смартфоны
INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('iphone'),('айфон'),('samsung'),('xiaomi'),('смартфон'),('телефон'),('android')
) AS t(kw) WHERE subcategories.name = 'Смартфоны'
    ON CONFLICT DO NOTHING;

-- Образование / Курсы
INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('курс'),('udemy'),('coursera'),('обучение'),('урок'),('лекция'),('тренинг'),('вебинар')
) AS t(kw) WHERE subcategories.name = 'Курсы'
    ON CONFLICT DO NOTHING;

-- Образование / Книги
INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('книга'),('книги'),('учебник'),('литература'),('читалка')
) AS t(kw) WHERE subcategories.name = 'Книги'
    ON CONFLICT DO NOTHING;

-- Животные / Корм
INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('корм'),('кошачий корм'),('собачий корм'),('pet food'),('вискас'),('pedigree')
) AS t(kw) WHERE subcategories.name = 'Корм'
    ON CONFLICT DO NOTHING;

-- Животные / Ветеринар
INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('ветеринар'),('ветклиника'),('vet'),('прививка животного'),('чипирование')
) AS t(kw) WHERE subcategories.name = 'Ветеринар'
    ON CONFLICT DO NOTHING;

-- Спорт / Абонемент
INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('фитнес'),('абонемент'),('gym'),('тренажёрный'),('crossfit'),('йога'),('бассейн'),('секция')
) AS t(kw) WHERE subcategories.name = 'Абонемент'
    ON CONFLICT DO NOTHING;

-- Путешествия / Перелёты
INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('авиабилет'),('перелёт'),('самолёт'),('авиа'),('аэрофлот'),('pegasus'),('билет авиа')
) AS t(kw) WHERE subcategories.name = 'Перелёты'
    ON CONFLICT DO NOTHING;

-- Путешествия / Отели
INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('отель'),('гостиница'),('хостел'),('airbnb'),('booking'),('апартаменты')
) AS t(kw) WHERE subcategories.name = 'Отели'
    ON CONFLICT DO NOTHING;

-- Связь / Мобильная связь
INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('мегафон'),('билайн'),('мтс'),('теле2'),('о2'),('симка'),('тариф'),('пополнение телефона')
) AS t(kw) WHERE subcategories.name = 'Мобильная связь'
    ON CONFLICT DO NOTHING;

-- Косметика / Парфюмерия
INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('духи'),('парфюм'),('туалетная вода'),('cologne'),('perfume')
) AS t(kw) WHERE subcategories.name = 'Парфюмерия'
    ON CONFLICT DO NOTHING;

-- Косметика / Уход за лицом
INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('крем'),('тоник'),('сыворотка'),('маска для лица'),('уход за кожей'),('spf'),('патчи')
) AS t(kw) WHERE subcategories.name = 'Уход за лицом'
    ON CONFLICT DO NOTHING;

-- Инвестиции / Крипто
INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('биткоин'),('bitcoin'),('крипто'),('usdt'),('binance'),('ethereum'),('криптовалюта')
) AS t(kw) WHERE subcategories.name = 'Крипто'
    ON CONFLICT DO NOTHING;

-- Подарки
INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('подарок'),('подарки'),('gift'),('букет'),('цветы'),('сувенир'),('праздник')
) AS t(kw) WHERE subcategories.name = 'День рождения'
    ON CONFLICT DO NOTHING;

-- Налоги / Бизнес (в категорию Налоги)
INSERT INTO subcategories (name, emoji, category_id)
SELECT 'Налог', '🧾', id FROM categories WHERE name = 'Налоги'
    ON CONFLICT DO NOTHING;

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('налог'),('ндс'),('налоговая'),('гнс'),('декларация'),('пошлина'),('штраф налог')
) AS t(kw) WHERE subcategories.name = 'Налог'
    ON CONFLICT DO NOTHING;

INSERT INTO subcategories (name, emoji, category_id)
SELECT 'Расход', '💼', id FROM categories WHERE name = 'Бизнес'
    ON CONFLICT DO NOTHING;

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('реклама'),('маркетинг'),('офис'),('аренда офис'),('канцелярия'),('командировка')
) AS t(kw) WHERE subcategories.name = 'Расход'
    ON CONFLICT DO NOTHING;