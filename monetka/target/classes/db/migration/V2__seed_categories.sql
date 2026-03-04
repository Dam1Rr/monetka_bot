-- ============================================================
-- Monetka Bot — Seed Default Categories
-- ============================================================

INSERT INTO categories (name, emoji, is_default) VALUES
    ('Еда',           '🍕', FALSE),
    ('Транспорт',     '🚗', FALSE),
    ('Развлечения',   '🎮', FALSE),
    ('Здоровье',      '💊', FALSE),
    ('Одежда',        '👕', FALSE),
    ('Жильё',         '🏠', FALSE),
    ('Связь',         '📱', FALSE),
    ('Образование',   '📚', FALSE),
    ('Прочее',        '💰', TRUE)
ON CONFLICT (name) DO NOTHING;

-- ---- Еда ----
INSERT INTO category_keywords (category_id, keyword)
SELECT id, kw FROM categories, UNNEST(ARRAY[
    'шаурма','пицца','суши','бургер','лаваш','роллы','плов','лагман','манты',
    'кофе','чай','сок','смузи',
    'обед','ужин','завтрак','перекус',
    'ресторан','кафе','столовая','фастфуд','доставка',
    'продукты','магазин','супермаркет','еда','рынок',
    'хлеб','молоко','мясо','рыба','овощи','фрукты',
    'макдак','kfc','бургер кинг'
]) AS kw
WHERE name = 'Еда'
ON CONFLICT DO NOTHING;

-- ---- Транспорт ----
INSERT INTO category_keywords (category_id, keyword)
SELECT id, kw FROM categories, UNNEST(ARRAY[
    'такси','метро','автобус','маршрутка','троллейбус','трамвай',
    'uber','яндекс такси','bolt','indriver',
    'бензин','заправка','парковка','штраф',
    'поезд','самолёт','билет','авиа'
]) AS kw
WHERE name = 'Транспорт'
ON CONFLICT DO NOTHING;

-- ---- Развлечения ----
INSERT INTO category_keywords (category_id, keyword)
SELECT id, kw FROM categories, UNNEST(ARRAY[
    'кино','театр','концерт','клуб','бар','ночной',
    'netflix','spotify','youtube','игры','стим','steam','playstation',
    'боулинг','бильярд','каток','квест',
    'подписка','музыка','фильм'
]) AS kw
WHERE name = 'Развлечения'
ON CONFLICT DO NOTHING;

-- ---- Здоровье ----
INSERT INTO category_keywords (category_id, keyword)
SELECT id, kw FROM categories, UNNEST(ARRAY[
    'аптека','лекарство','таблетки','витамины',
    'врач','доктор','клиника','больница','анализы',
    'спортзал','фитнес','тренажёрный','бассейн','йога',
    'стоматолог','зубной','массаж'
]) AS kw
WHERE name = 'Здоровье'
ON CONFLICT DO NOTHING;

-- ---- Одежда ----
INSERT INTO category_keywords (category_id, keyword)
SELECT id, kw FROM categories, UNNEST(ARRAY[
    'одежда','обувь','кроссовки','куртка','пальто','джинсы',
    'футболка','рубашка','платье','юбка','носки',
    'сумка','рюкзак','кепка','шапка',
    'zara','h&m','adidas','nike'
]) AS kw
WHERE name = 'Одежда'
ON CONFLICT DO NOTHING;

-- ---- Жильё ----
INSERT INTO category_keywords (category_id, keyword)
SELECT id, kw FROM categories, UNNEST(ARRAY[
    'аренда','квартира','съём','коммуналка',
    'электричество','вода','газ','отопление',
    'интернет','wifi','роутер',
    'мебель','ремонт','хозтовары'
]) AS kw
WHERE name = 'Жильё'
ON CONFLICT DO NOTHING;

-- ---- Связь ----
INSERT INTO category_keywords (category_id, keyword)
SELECT id, kw FROM categories, UNNEST(ARRAY[
    'телефон','мтс','билайн','мегафон','теле2',
    'симкарта','тариф','пополнение','звонки'
]) AS kw
WHERE name = 'Связь'
ON CONFLICT DO NOTHING;

-- ---- Образование ----
INSERT INTO category_keywords (category_id, keyword)
SELECT id, kw FROM categories, UNNEST(ARRAY[
    'курс','обучение','учёба','университет','институт',
    'книга','учебник','тетрадь','канцелярия',
    'репетитор','урок','тренинг'
]) AS kw
WHERE name = 'Образование'
ON CONFLICT DO NOTHING;
