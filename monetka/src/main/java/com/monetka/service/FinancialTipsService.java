package com.monetka.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Генерирует реальные финансовые советы с расчётами.
 */
@Service
public class FinancialTipsService {

    private static final List<String> GENERAL_TIPS = List.of(
            "Правило 50/30/20: 50% на нужды, 30% на желания, 20% на накопления.",
            "Откладывай минимум 10% от каждого дохода — это фундамент финансовой подушки.",
            "Финансовая подушка = 3–6 месяцев твоих расходов. Начни формировать её сегодня.",
            "Записывай расходы каждый день — это само по себе снижает траты на 15–20%.",
            "Покупай только то, что планировал. Импульсивные покупки — главный враг бюджета.",
            "Сравнивай цены перед крупными покупками — можно сэкономить 10–30%."
    );

    /**
     * Совет с расчётом годовой стоимости подписки.
     * Например: "Netflix 1500 сом/мес = 18 000 сом/год — на эти деньги можно купить наушники TWS."
     */
    public String tipForSubscription(String name, BigDecimal monthlyAmount) {
        BigDecimal yearly = monthlyAmount.multiply(BigDecimal.valueOf(12));

        String whatToBuy = whatCanYouBuyFor(yearly);
        String saving    = savingTip(monthlyAmount);

        return String.format(
                "💡 *Финансовый факт:*\n" +
                        "_%s_ стоит *%s/мес*\n" +
                        "В год это: *%s*\n" +
                        "%s\n\n" +
                        "%s",
                name,
                fmt(monthlyAmount),
                fmt(yearly),
                whatToBuy,
                saving
        );
    }

    /** Общий случайный совет */
    public String randomTip() {
        return "💡 " + GENERAL_TIPS.get(ThreadLocalRandom.current().nextInt(GENERAL_TIPS.size()));
    }

    // ---- Helpers ----

    private String whatCanYouBuyFor(BigDecimal amount) {
        long sum = amount.longValue();

        if (sum < 1_000)  return "☕ На эти деньги можно выпить пару чашек кофе.";
        if (sum < 3_000)  return "🍕 Это несколько походов в кафе.";
        if (sum < 8_000)  return "👟 Можно купить неплохие кроссовки на рынке.";
        if (sum < 15_000) return "🎧 Хватит на хорошие TWS-наушники.";
        if (sum < 30_000) return "📱 Это бюджетный смартфон среднего класса.";
        if (sum < 60_000) return "💻 Можно купить б/у ноутбук для работы.";
        if (sum < 120_000) return "✈️ Хватит на авиабилет туда-обратно в Стамбул.";
        return "🏦 Серьёзная сумма — стоит задуматься об инвестициях!";
    }

    private String savingTip(BigDecimal monthly) {
        BigDecimal yearly = monthly.multiply(BigDecimal.valueOf(12));
        BigDecimal saved  = yearly.multiply(BigDecimal.valueOf(0.1)).setScale(0, RoundingMode.HALF_UP);
        return String.format("Если отложить 10%% от этой суммы = *%s/год* на накопления.", fmt(saved));
    }

    private String fmt(BigDecimal amount) {
        return String.format("%,.0f сом", amount);
    }
}