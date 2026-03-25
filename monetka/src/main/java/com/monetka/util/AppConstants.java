package com.monetka.util;

import java.math.BigDecimal;
import java.time.ZoneId;

/**
 * Shared constants and formatting helpers used across the entire application.
 * Eliminates copy-paste of ZoneId and fmt() in every service/handler.
 */
public final class AppConstants {

    public static final ZoneId BISHKEK = ZoneId.of("Asia/Bishkek");

    private AppConstants() {}

    /** Format a monetary amount as "1 234 сом" */
    public static String fmt(BigDecimal amount) {
        return String.format("%,.0f сом", amount);
    }
}
