# 💰 Monetka Bot

Минималистичный Telegram финансовый помощник.

## Стек
- Java 21 + Spring Boot 3.2
- PostgreSQL + Flyway
- Telegram Webhook
- Railway deployment

## Быстрый старт

### 1. Создай бота
1. Открой [@BotFather](https://t.me/BotFather) в Telegram
2. `/newbot` → получи `BOT_TOKEN`
3. Запомни username бота

### 2. Локальный запуск

```bash
# Клонируй и собери
git clone ...
cd monetka
mvn clean package -DskipTests

# Запусти PostgreSQL
docker run -d -p 5432:5432 -e POSTGRES_DB=monetka \
  -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres \
  postgres:16

# Запусти с ngrok для тестирования webhook
ngrok http 8080
# Скопируй https URL от ngrok

# Запусти приложение
BOT_TOKEN=xxx BOT_USERNAME=xxx WEBHOOK_URL=https://xxx.ngrok.io \
  DATABASE_URL=jdbc:postgresql://localhost:5432/monetka \
  DATABASE_USERNAME=postgres DATABASE_PASSWORD=postgres \
  ADMIN_IDS=your_telegram_id \
  java -jar target/monetka-bot-1.0.0.jar
```

### 3. Деплой на Railway

1. **Fork** этот репозиторий
2. Открой [Railway](https://railway.app) → New Project → GitHub
3. Добавь **PostgreSQL** сервис в проект
4. В Variables добавь переменные из `.env.example`:
   ```
   BOT_TOKEN=...
   BOT_USERNAME=...
   WEBHOOK_URL=https://your-app.up.railway.app
   ADMIN_IDS=your_telegram_id
   ```
5. Railway автоматически подставит `DATABASE_URL`

---

## Функции

| Функция | Описание |
|---------|----------|
| `/start` | Регистрация + главное меню |
| 💸 Расход | `шаурма 300` — авто-определение категории |
| 💰 Доход | `зарплата 150000` |
| 📊 Статистика | Расходы по категориям за месяц |
| 💳 Баланс | Текущий баланс |
| 🔄 Подписки | Ежемесячные авто-списания |
| 21:00 daily | Автоматический отчёт за день |

## Архитектура

```
controller/   ← WebhookController (принимает update от Telegram)
bot/          ← MonetkaBot + UpdateDispatcher + handlers + keyboard
service/      ← Бизнес-логика
repository/   ← Spring Data JPA
model/        ← JPA entities + enums
scheduler/    ← Daily reports, subscription charges
config/       ← Properties, webhook registration
```

## Добавление новых категорий

Добавь в `V2__seed_categories.sql` или через SQL:
```sql
INSERT INTO categories (name, emoji) VALUES ('Путешествия', '✈️');
INSERT INTO category_keywords (category_id, keyword)
SELECT id, kw FROM categories, UNNEST(ARRAY['отель','билет','тур']) kw
WHERE name = 'Путешествия';
```

После добавления вызови `CategoryDetectionService.reload()`.
