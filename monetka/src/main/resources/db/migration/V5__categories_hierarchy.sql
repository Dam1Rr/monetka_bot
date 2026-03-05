-- Очищаем старые данные
DELETE FROM category_keywords;
DELETE FROM categories CASCADE;

-- Создаём таблицу подкатегорий если нет
CREATE TABLE IF NOT EXISTS subcategories (
                                             id          BIGSERIAL PRIMARY KEY,
                                             name        VARCHAR(100) NOT NULL,
    emoji       VARCHAR(10),
    category_id BIGINT NOT NULL REFERENCES categories(id) ON DELETE CASCADE
    );

CREATE TABLE IF NOT EXISTS subcategory_keywords (
                                                    subcategory_id BIGINT NOT NULL REFERENCES subcategories(id) ON DELETE CASCADE,
    keyword        VARCHAR(100) NOT NULL
    );

CREATE TABLE IF NOT EXISTS learned_keywords (
                                                id             BIGSERIAL PRIMARY KEY,
                                                keyword        VARCHAR(200) NOT NULL,
    category_id    BIGINT NOT NULL REFERENCES categories(id),
    subcategory_id BIGINT REFERENCES subcategories(id),
    user_id        BIGINT,
    use_count      INT DEFAULT 1,
    created_at     TIMESTAMP DEFAULT NOW(),
    UNIQUE (keyword, user_id)
    );

-- ================================================================
-- КАТЕГОРИИ
-- ================================================================

INSERT INTO categories (name, emoji, is_default) VALUES
                                                     ('Еда',          '🍕', false),
                                                     ('Транспорт',    '🚌', false),
                                                     ('Авто',         '🚗', false),
                                                     ('Дом',          '🏠', false),
                                                     ('Развлечения',  '🎉', false),
                                                     ('Здоровье',     '💊', false),
                                                     ('Покупки',      '🛍', false),
                                                     ('Подписки',     '📱', false),
                                                     ('Прочее',       '💰', true);

-- ================================================================
-- ЕДА — подкатегории и ключевые слова
-- ================================================================

INSERT INTO subcategories (name, emoji, category_id) VALUES
                                                         ('Фастфуд',      '🍔', (SELECT id FROM categories WHERE name='Еда')),
                                                         ('Кафе',         '☕', (SELECT id FROM categories WHERE name='Еда')),
                                                         ('Рестораны',    '🍽', (SELECT id FROM categories WHERE name='Еда')),
                                                         ('Продукты',     '🛒', (SELECT id FROM categories WHERE name='Еда')),
                                                         ('Сладости',     '🍰', (SELECT id FROM categories WHERE name='Еда')),
                                                         ('Напитки',      '🥤', (SELECT id FROM categories WHERE name='Еда'));

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('шаурма'),('шаверма'),('шурма'),('бургер'),('бургеры'),('чипсы'),
                                       ('наггетсы'),('донер'),('kfc'),('мак'),('макдак'),('макдональдс'),
                                       ('бургеркинг'),('burger king'),('пицца'),('фастфуд'),('хотдог'),
                                       ('стритфуд'),('картошка фри'),('ролл'),('лаваш'),('самса'),('хинкали'),
                                       ('манты'),('лагман'),('плов'),('doner'),('pizza'),('shawarma')
) AS t(kw) WHERE subcategories.name='Фастфуд';

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('кафе'),('кофе'),('капучино'),('латте'),('эспрессо'),('американо'),
                                       ('starbucks'),('coffee'),('завтрак'),('обед'),('бизнес ланч'),('ланч'),
                                       ('столовая'),('канteen'),('буфет'),('перекус')
) AS t(kw) WHERE subcategories.name='Кафе';

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('ресторан'),('суши'),('sushi'),('роллы'),('японский'),('китайский'),
                                       ('итальянский'),('ужин'),('банкет'),('доставка еды'),('яндекс еда'),
                                       ('glovo'),('wolt'),('delivery')
) AS t(kw) WHERE subcategories.name='Рестораны';

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('продукты'),('магазин'),('супермаркет'),('гипермаркет'),('ашан'),
                                       ('metro'),('Fix Price'),('магнит'),('пятерочка'),('дикси'),('глобус'),
                                       ('овощи'),('фрукты'),('мясо'),('молоко'),('хлеб'),('яйца'),('крупа'),
                                       ('grocery'),('еда домой'),('продуктовый')
) AS t(kw) WHERE subcategories.name='Продукты';

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('торт'),('пирожное'),('мороженое'),('конфеты'),('шоколад'),('печенье'),
                                       ('пончик'),('вафли'),('десерт'),('сладкое'),('candy'),('cake')
) AS t(kw) WHERE subcategories.name='Сладости';

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('напиток'),('сок'),('вода'),('газировка'),('компот'),('чай'),('лимонад'),
                                       ('энергетик'),('smoothie'),('смузи'),('juice')
) AS t(kw) WHERE subcategories.name='Напитки';

-- ================================================================
-- ТРАНСПОРТ
-- ================================================================

INSERT INTO subcategories (name, emoji, category_id) VALUES
                                                         ('Общественный транспорт', '🚌', (SELECT id FROM categories WHERE name='Транспорт')),
                                                         ('Такси',                  '🚖', (SELECT id FROM categories WHERE name='Транспорт')),
                                                         ('Самокат',                '🛴', (SELECT id FROM categories WHERE name='Транспорт')),
                                                         ('Межгород',               '🚆', (SELECT id FROM categories WHERE name='Транспорт'));

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('автобус'),('маршрутка'),('троллейбус'),('метро'),('трамвай'),
                                       ('проезд'),('билет'),('транспорт'),('электричка'),('мтс'),('карта тройка')
) AS t(kw) WHERE subcategories.name='Общественный транспорт';

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('такси'),('uber'),('яндекс такси'),('яндекс го'),('yandex'),
                                       ('indriver'),('bolt'),('максим'),('cabify')
) AS t(kw) WHERE subcategories.name='Такси';

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('самокат'),('велосипед'),('аренда'),('кикшеринг'),('whoosh'),('urent')
) AS t(kw) WHERE subcategories.name='Самокат';

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('поезд'),('авиа'),('самолет'),('билет'),('жд'),('аэропорт'),
                                       ('автовокзал'),('автобус межгород')
) AS t(kw) WHERE subcategories.name='Межгород';

-- ================================================================
-- АВТО
-- ================================================================

INSERT INTO subcategories (name, emoji, category_id) VALUES
                                                         ('Топливо',  '⛽', (SELECT id FROM categories WHERE name='Авто')),
                                                         ('Ремонт',   '🔧', (SELECT id FROM categories WHERE name='Авто')),
                                                         ('Парковка', '🅿', (SELECT id FROM categories WHERE name='Авто')),
                                                         ('Мойка',    '🚿', (SELECT id FROM categories WHERE name='Авто')),
                                                         ('Запчасти', '⚙', (SELECT id FROM categories WHERE name='Авто'));

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('бензин'),('бенза'),('бенз'),('топливо'),('заправка'),('азс'),
                                       ('дизель'),('92'),('95'),('98'),('газ'),('лукойл'),('газпром'),('shell')
) AS t(kw) WHERE subcategories.name='Топливо';

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('ремонт авто'),('сто'),('автосервис'),('масло'),('замена масла'),
                                       ('техосмотр'),('тосол'),('антифриз'),('шиномонтаж')
) AS t(kw) WHERE subcategories.name='Ремонт';

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('парковка'),('стоянка'),('паркомат'),('parking')
) AS t(kw) WHERE subcategories.name='Парковка';

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('мойка'),('автомойка'),('carwash')
) AS t(kw) WHERE subcategories.name='Мойка';

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('запчасти'),('автозапчасти'),('фильтр'),('аккумулятор'),('колесо'),('шины')
) AS t(kw) WHERE subcategories.name='Запчасти';

-- ================================================================
-- ДОМ
-- ================================================================

INSERT INTO subcategories (name, emoji, category_id) VALUES
                                                         ('Коммуналка', '💡', (SELECT id FROM categories WHERE name='Дом')),
                                                         ('Интернет',   '🌐', (SELECT id FROM categories WHERE name='Дом')),
                                                         ('Аренда',     '🏠', (SELECT id FROM categories WHERE name='Дом')),
                                                         ('Мебель',     '🛋', (SELECT id FROM categories WHERE name='Дом')),
                                                         ('Ремонт',     '🔨', (SELECT id FROM categories WHERE name='Дом'));

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('коммуналка'),('коммунальные'),('свет'),('вода'),('газ'),('квитанция'),
                                       ('жкх'),('электричество'),('отопление'),('счётчик')
) AS t(kw) WHERE subcategories.name='Коммуналка';

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('интернет'),('wifi'),('вайфай'),('роутер'),('сим'),('симка'),
                                       ('мобильный'),('телефон'),('связь'),('мегафон'),('билайн'),('мтс'),('теле2')
) AS t(kw) WHERE subcategories.name='Интернет';

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('аренда'),('квартира'),('съём'),('жильё'),('хозяйка'),('хозяин')
) AS t(kw) WHERE subcategories.name='Аренда';

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('мебель'),('диван'),('стол'),('стул'),('шкаф'),('кровать'),('ikea')
) AS t(kw) WHERE subcategories.name='Мебель';

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('ремонт'),('стройматериалы'),('краска'),('плитка'),('обои'),('леруа'),('obi')
) AS t(kw) WHERE subcategories.name='Ремонт';

-- ================================================================
-- РАЗВЛЕЧЕНИЯ
-- ================================================================

INSERT INTO subcategories (name, emoji, category_id) VALUES
                                                         ('Кино',          '🎬', (SELECT id FROM categories WHERE name='Развлечения')),
                                                         ('Игры',          '🎮', (SELECT id FROM categories WHERE name='Развлечения')),
                                                         ('Бары',          '🍺', (SELECT id FROM categories WHERE name='Развлечения')),
                                                         ('Клубы',         '🕺', (SELECT id FROM categories WHERE name='Развлечения')),
                                                         ('Баня',          '🧖', (SELECT id FROM categories WHERE name='Развлечения')),
                                                         ('Путешествия',   '✈', (SELECT id FROM categories WHERE name='Развлечения'));

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('кино'),('кинотеатр'),('cinema'),('фильм'),('билет кино'),('imax')
) AS t(kw) WHERE subcategories.name='Кино';

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('игра'),('steam'),('playstation'),('xbox'),('nintendo'),('донат'),
                                       ('игры'),('gaming'),('gta'),('minecraft')
) AS t(kw) WHERE subcategories.name='Игры';

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('бар'),('пиво'),('пивная'),('паб'),('beer'),('напитки'),('алкоголь'),
                                       ('вино'),('коктейль')
) AS t(kw) WHERE subcategories.name='Бары';

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('клуб'),('ночной'),('дискотека'),('вход'),('фейс контроль')
) AS t(kw) WHERE subcategories.name='Клубы';

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('баня'),('банька'),('сауна'),('парилка'),('хамам'),('spa'),('спа'),('бассейн')
) AS t(kw) WHERE subcategories.name='Баня';

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('отель'),('гостиница'),('хостел'),('airbnb'),('тур'),('путёвка'),
                                       ('виза'),('экскурсия'),('туризм')
) AS t(kw) WHERE subcategories.name='Путешествия';

-- ================================================================
-- ЗДОРОВЬЕ
-- ================================================================

INSERT INTO subcategories (name, emoji, category_id) VALUES
                                                         ('Аптека',  '💊', (SELECT id FROM categories WHERE name='Здоровье')),
                                                         ('Врачи',   '👨‍⚕', (SELECT id FROM categories WHERE name='Здоровье')),
                                                         ('Спорт',   '⚽', (SELECT id FROM categories WHERE name='Здоровье')),
                                                         ('Фитнес',  '💪', (SELECT id FROM categories WHERE name='Здоровье'));

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('аптека'),('лекарства'),('таблетки'),('витамины'),('препарат'),
                                       ('лекарство'),('pharmacy')
) AS t(kw) WHERE subcategories.name='Аптека';

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('врач'),('доктор'),('клиника'),('больница'),('анализы'),('укол'),
                                       ('стоматолог'),('зубной'),('hospital'),('медицина')
) AS t(kw) WHERE subcategories.name='Врачи';

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('спорт'),('футбол'),('баскетбол'),('теннис'),('бокс'),('единоборства'),
                                       ('зал'),('спортзал'),('тренировка'),('секция')
) AS t(kw) WHERE subcategories.name='Спорт';

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('фитнес'),('абонемент'),('gym'),('кросфит'),('crossfit'),('йога'),
                                       ('yoga'),('пилатес'),('тренер')
) AS t(kw) WHERE subcategories.name='Фитнес';

-- ================================================================
-- ПОКУПКИ
-- ================================================================

INSERT INTO subcategories (name, emoji, category_id) VALUES
                                                         ('Одежда',       '👕', (SELECT id FROM categories WHERE name='Покупки')),
                                                         ('Техника',      '💻', (SELECT id FROM categories WHERE name='Покупки')),
                                                         ('Электроника',  '📱', (SELECT id FROM categories WHERE name='Покупки')),
                                                         ('Маркетплейс',  '📦', (SELECT id FROM categories WHERE name='Покупки'));

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('одежда'),('кроссовки'),('обувь'),('штаны'),('футболка'),('куртка'),
                                       ('платье'),('джинсы'),('nike'),('adidas'),('zara'),('h&m')
) AS t(kw) WHERE subcategories.name='Одежда';

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('ноутбук'),('компьютер'),('пк'),('моноблок'),('принтер'),('монитор'),
                                       ('laptop'),('apple'),('macbook')
) AS t(kw) WHERE subcategories.name='Техника';

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('телефон'),('смартфон'),('наушники'),('айфон'),('iphone'),('samsung'),
                                       ('android'),('xiaomi'),('планшет'),('tablet'),('зарядка'),('кабель')
) AS t(kw) WHERE subcategories.name='Электроника';

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('озон'),('ozon'),('wildberries'),('wb'),('amazon'),('aliexpress'),
                                       ('ali'),('яндекс маркет'),('lamoda'),('сдэк'),('посылка')
) AS t(kw) WHERE subcategories.name='Маркетплейс';

-- ================================================================
-- ПОДПИСКИ
-- ================================================================

INSERT INTO subcategories (name, emoji, category_id) VALUES
                                                         ('Видео',    '🎬', (SELECT id FROM categories WHERE name='Подписки')),
                                                         ('Музыка',   '🎵', (SELECT id FROM categories WHERE name='Подписки')),
                                                         ('Сервисы',  '☁', (SELECT id FROM categories WHERE name='Подписки')),
                                                         ('Софт',     '💾', (SELECT id FROM categories WHERE name='Подписки'));

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('netflix'),('нетфликс'),('youtube'),('ютуб'),('кинопоиск'),('иви'),('ivi'),
                                       ('okko'),('premier'),('hbo'),('disney')
) AS t(kw) WHERE subcategories.name='Видео';

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('spotify'),('спотифай'),('яндекс музыка'),('apple music'),
                                       ('звук'),('дeezer'),('tidal')
) AS t(kw) WHERE subcategories.name='Музыка';

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('облако'),('icloud'),('google one'),('dropbox'),('яндекс диск'),
                                       ('telegram premium'),('chatgpt'),('claude')
) AS t(kw) WHERE subcategories.name='Сервисы';

INSERT INTO subcategory_keywords (subcategory_id, keyword)
SELECT id, kw FROM subcategories, (VALUES
                                       ('антивирус'),('windows'),('office'),('adobe'),('figma'),('notion'),
                                       ('подписка'),('pro'),('premium')
) AS t(kw) WHERE subcategories.name='Софт';

-- Добавляем subcategory_id в transactions
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS subcategory_id BIGINT REFERENCES subcategories(id);