package com.monetka.admin;

import com.monetka.model.User;
import com.monetka.model.enums.UserStatus;
import com.monetka.repository.*;
import com.monetka.service.CategoryDetectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Business logic for the admin panel:
 * user management, bot statistics, and the nuclear "wipe" operation.
 */
@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private final UserRepository           userRepository;
    private final TransactionRepository    transactionRepository;
    private final SubscriptionRepository   subscriptionRepository;
    private final CategoryRepository       categoryRepository;
    private final SubcategoryRepository    subcategoryRepository;
    private final LearnedKeywordRepository learnedKeywordRepository;
    private final JdbcTemplate             jdbc;
    private final CategoryDetectionService detectionService;

    public AdminService(UserRepository userRepository,
                        TransactionRepository transactionRepository,
                        SubscriptionRepository subscriptionRepository,
                        CategoryRepository categoryRepository,
                        SubcategoryRepository subcategoryRepository,
                        LearnedKeywordRepository learnedKeywordRepository,
                        JdbcTemplate jdbc,
                        CategoryDetectionService detectionService) {
        this.userRepository           = userRepository;
        this.transactionRepository    = transactionRepository;
        this.subscriptionRepository   = subscriptionRepository;
        this.categoryRepository       = categoryRepository;
        this.subcategoryRepository    = subcategoryRepository;
        this.learnedKeywordRepository = learnedKeywordRepository;
        this.jdbc                     = jdbc;
        this.detectionService         = detectionService;
    }

    // ================================================================
    // User queries
    // ================================================================

    public List<User> getPendingUsers()  { return userRepository.findAllByStatus(UserStatus.PENDING); }
    public List<User> getBlockedUsers()  { return userRepository.findAllByStatus(UserStatus.BLOCKED); }
    public List<User> getActiveUsers() { return userRepository.findAllByStatus(UserStatus.ACTIVE); }

    // ================================================================
    // Status mutations
    // ================================================================

    @Transactional
    public Optional<User> approveUser(Long telegramId) {
        return userRepository.findByTelegramId(telegramId).map(u -> {
            u.setStatus(UserStatus.ACTIVE);
            log.info("Admin approved user {}", telegramId);
            return userRepository.save(u);
        });
    }

    @Transactional
    public Optional<User> rejectUser(Long telegramId) {
        return userRepository.findByTelegramId(telegramId).map(u -> {
            u.setStatus(UserStatus.BLOCKED);
            log.info("Admin rejected user {}", telegramId);
            return userRepository.save(u);
        });
    }

    @Transactional
    public Optional<User> blockUser(Long telegramId) {
        return userRepository.findByTelegramId(telegramId).map(u -> {
            u.setStatus(UserStatus.BLOCKED);
            log.info("Admin blocked user {}", telegramId);
            return userRepository.save(u);
        });
    }

    @Transactional
    public Optional<User> unblockUser(Long telegramId) {
        return userRepository.findByTelegramId(telegramId).map(u -> {
            u.setStatus(UserStatus.ACTIVE);
            log.info("Admin unblocked user {}", telegramId);
            return userRepository.save(u);
        });
    }

    // ================================================================
    // Statistics
    // ================================================================

    public AdminStats getStats() {
        long total    = userRepository.count();
        long active   = userRepository.countByStatus(UserStatus.ACTIVE);
        long pending  = userRepository.countByStatus(UserStatus.PENDING);
        long blocked  = userRepository.countByStatus(UserStatus.BLOCKED);
        long txCount  = transactionRepository.count();
        long subCount = subscriptionRepository.count();

        BigDecimal expenses = transactionRepository.sumAllExpenses();
        if (expenses == null) expenses = BigDecimal.ZERO;

        return new AdminStats(total, active, pending, blocked, txCount, expenses, subCount);
    }

    // ================================================================
    // Nuclear wipe — deletes everything, reseeds categories
    // ================================================================

    @Transactional
    public void resetDatabase() {
        log.warn("ADMIN: Starting full database reset");

        // Delete in FK-safe order
        jdbc.execute("DELETE FROM learned_keywords");
        jdbc.execute("DELETE FROM subcategory_keywords");
        jdbc.execute("DELETE FROM transactions");
        jdbc.execute("DELETE FROM subscriptions");
        jdbc.execute("DELETE FROM subcategories");
        jdbc.execute("DELETE FROM categories");
        jdbc.execute("DELETE FROM users");

        // Reset sequences
        jdbc.execute("ALTER SEQUENCE IF EXISTS users_id_seq           RESTART WITH 1");
        jdbc.execute("ALTER SEQUENCE IF EXISTS transactions_id_seq    RESTART WITH 1");
        jdbc.execute("ALTER SEQUENCE IF EXISTS subscriptions_id_seq   RESTART WITH 1");
        jdbc.execute("ALTER SEQUENCE IF EXISTS categories_id_seq      RESTART WITH 1");
        jdbc.execute("ALTER SEQUENCE IF EXISTS subcategories_id_seq   RESTART WITH 1");
        jdbc.execute("ALTER SEQUENCE IF EXISTS learned_keywords_id_seq RESTART WITH 1");

        // Reseed default categories
        reseedCategories();

        // Rebuild in-memory keyword index
        detectionService.reload();

        log.warn("ADMIN: Database reset complete");
    }

    // ================================================================
    // Category seed — mirrors V5__categories_hierarchy.sql
    // ================================================================

    private void reseedCategories() {
        // --- Main categories ---
        jdbc.execute("""
            INSERT INTO categories (name, emoji, is_default) VALUES
              ('Еда',          '🍕', false),
              ('Транспорт',    '🚌', false),
              ('Авто',         '🚗', false),
              ('Дом',          '🏠', false),
              ('Развлечения',  '🎉', false),
              ('Здоровье',     '💊', false),
              ('Покупки',      '🛍', false),
              ('Подписки',     '📱', false),
              ('Прочее',       '💰', true)
        """);

        // --- Subcategories per category ---
        insertSubcats("Еда",
                sub("Фастфуд",    "🍔"),
                sub("Кафе",       "☕"),
                sub("Рестораны",  "🍽"),
                sub("Продукты",   "🛒"),
                sub("Сладости",   "🍰"),
                sub("Напитки",    "🥤")
        );
        insertSubcats("Транспорт",
                sub("Общественный транспорт", "🚌"),
                sub("Такси",                  "🚖"),
                sub("Самокат",                "🛴"),
                sub("Межгород",               "🚆")
        );
        insertSubcats("Авто",
                sub("Топливо",  "⛽"),
                sub("Ремонт",   "🔧"),
                sub("Парковка", "🅿"),
                sub("Мойка",    "🚿"),
                sub("Запчасти", "⚙")
        );
        insertSubcats("Дом",
                sub("Коммуналка", "💡"),
                sub("Интернет",   "🌐"),
                sub("Аренда",     "🏠"),
                sub("Мебель",     "🛋"),
                sub("Ремонт",     "🔨")
        );
        insertSubcats("Развлечения",
                sub("Кино",        "🎬"),
                sub("Игры",        "🎮"),
                sub("Бары",        "🍺"),
                sub("Клубы",       "🕺"),
                sub("Баня",        "🧖"),
                sub("Путешествия", "✈")
        );
        insertSubcats("Здоровье",
                sub("Аптека", "💊"),
                sub("Врачи",  "👨‍⚕"),
                sub("Спорт",  "⚽"),
                sub("Фитнес", "💪")
        );
        insertSubcats("Покупки",
                sub("Одежда",      "👕"),
                sub("Техника",     "💻"),
                sub("Электроника", "📱"),
                sub("Маркетплейс", "📦")
        );
        insertSubcats("Подписки",
                sub("Видео",   "🎬"),
                sub("Музыка",  "🎵"),
                sub("Сервисы", "☁"),
                sub("Софт",    "💾")
        );

        // --- Keywords ---
        insertKws("Фастфуд",    "шаурма","шаверма","шурма","бургер","бургеры","чипсы","наггетсы","донер","kfc","мак","макдак","макдональдс","бургеркинг","пицца","фастфуд","хотдог","стритфуд","ролл","лаваш","самса","хинкали","манты","лагман","плов");
        insertKws("Кафе",       "кафе","кофе","капучино","латте","эспрессо","американо","starbucks","coffee","завтрак","обед","бизнес ланч","ланч","столовая","буфет","перекус");
        insertKws("Рестораны",  "ресторан","суши","sushi","роллы","японский","китайский","итальянский","ужин","банкет","доставка еды","яндекс еда","glovo","wolt","delivery");
        insertKws("Продукты",   "продукты","магазин","супермаркет","гипермаркет","ашан","metro","Fix Price","овощи","фрукты","мясо","молоко","хлеб","яйца","крупа","grocery","продуктовый");
        insertKws("Сладости",   "торт","пирожное","мороженое","конфеты","шоколад","печенье","пончик","вафли","десерт","сладкое","candy","cake");
        insertKws("Напитки",    "напиток","сок","вода","газировка","компот","чай","лимонад","энергетик","smoothie","смузи","juice");
        insertKws("Общественный транспорт","автобус","маршрутка","троллейбус","метро","трамвай","проезд","билет","транспорт","электричка");
        insertKws("Такси",      "такси","uber","яндекс такси","яндекс го","yandex","indriver","bolt","максим");
        insertKws("Самокат",    "самокат","велосипед","аренда","кикшеринг","whoosh","urent");
        insertKws("Межгород",   "поезд","авиа","самолет","билет","жд","аэропорт","автовокзал");
        insertKws("Топливо",    "бензин","бенза","бенз","топливо","заправка","азс","дизель","92","95","98","газ","лукойл","газпром","shell");
        insertKws("Парковка",   "парковка","стоянка","паркомат","parking");
        insertKws("Мойка",      "мойка","автомойка","carwash");
        insertKws("Запчасти",   "запчасти","автозапчасти","фильтр","аккумулятор","колесо","шины");
        insertKws("Коммуналка", "коммуналка","коммунальные","свет","вода","газ","квитанция","жкх","электричество","отопление","счётчик");
        insertKws("Интернет",   "интернет","wifi","вайфай","роутер","сим","симка","мобильный","телефон","связь","мегафон","билайн","мтс","теле2");
        insertKws("Аренда",     "аренда","квартира","съём","жильё","хозяйка","хозяин");
        insertKws("Мебель",     "мебель","диван","стол","стул","шкаф","кровать","ikea");
        insertKws("Кино",       "кино","кинотеатр","cinema","фильм","imax");
        insertKws("Игры",       "игра","steam","playstation","xbox","nintendo","донат","игры","gaming","gta","minecraft");
        insertKws("Бары",       "бар","пиво","пивная","паб","beer","алкоголь","вино","коктейль");
        insertKws("Клубы",      "клуб","ночной","дискотека","вход");
        insertKws("Баня",       "баня","банька","сауна","парилка","хамам","spa","спа","бассейн");
        insertKws("Путешествия","отель","гостиница","хостел","airbnb","тур","путёвка","виза","экскурсия","туризм");
        insertKws("Аптека",     "аптека","лекарства","таблетки","витамины","препарат","лекарство","pharmacy");
        insertKws("Врачи",      "врач","доктор","клиника","больница","анализы","укол","стоматолог","зубной","hospital","медицина");
        insertKws("Спорт",      "спорт","футбол","баскетбол","теннис","бокс","единоборства","зал","спортзал","тренировка","секция");
        insertKws("Фитнес",     "фитнес","абонемент","gym","кросфит","crossfit","йога","yoga","пилатес","тренер");
        insertKws("Одежда",     "одежда","кроссовки","обувь","штаны","футболка","куртка","платье","джинсы","nike","adidas","zara","h&m");
        insertKws("Техника",    "ноутбук","компьютер","пк","моноблок","принтер","монитор","laptop","apple","macbook");
        insertKws("Электроника","телефон","смартфон","наушники","айфон","iphone","samsung","android","xiaomi","планшет","tablet","зарядка","кабель");
        insertKws("Маркетплейс","озон","ozon","wildberries","wb","amazon","aliexpress","ali","яндекс маркет","lamoda","посылка");
        insertKws("Видео",      "netflix","нетфликс","youtube","ютуб","кинопоиск","иви","ivi","okko","premier","hbo","disney");
        insertKws("Музыка",     "spotify","спотифай","яндекс музыка","apple music","звук","tidal");
        insertKws("Сервисы",    "облако","icloud","google one","dropbox","яндекс диск","telegram premium","chatgpt","claude");
        insertKws("Софт",       "антивирус","windows","office","adobe","figma","notion","подписка","pro","premium");
    }

    // ---- Seed helpers ----

    private record SubDef(String name, String emoji) {}

    private SubDef sub(String name, String emoji) { return new SubDef(name, emoji); }

    private void insertSubcats(String categoryName, SubDef... subs) {
        for (SubDef s : subs) {
            jdbc.update("""
                INSERT INTO subcategories (name, emoji, category_id)
                SELECT ?, ?, id FROM categories WHERE name = ?
            """, s.name(), s.emoji(), categoryName);
        }
    }

    private void insertKws(String subcategoryName, String... keywords) {
        for (String kw : keywords) {
            jdbc.update("""
                INSERT INTO subcategory_keywords (subcategory_id, keyword)
                SELECT id, ? FROM subcategories WHERE name = ?
                ON CONFLICT DO NOTHING
            """, kw, subcategoryName);
        }
    }
}