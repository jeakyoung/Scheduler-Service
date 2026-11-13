package com.scheduler.web.api;

/**
 * Never return target_db_config passwords or credential values in plaintext over the API
 * (PLAN.md §6) - only the last 4 characters are shown, the rest replaced with '*'.
 */
final class Masking {

    private Masking() {
    }

    static String lastFour(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        int visible = Math.min(4, value.length());
        String tail = value.substring(value.length() - visible);
        return "*".repeat(value.length() - visible) + tail;
    }
}
