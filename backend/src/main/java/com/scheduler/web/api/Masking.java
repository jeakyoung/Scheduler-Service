package com.scheduler.web.api;

/**
 * Never return target_db_config passwords or credential values in plaintext over the API
 * (PLAN.md §6) - only the last 4 characters are shown, the rest replaced with '*'.
 */
final class Masking {

    private static final int VISIBLE_SUFFIX_LENGTH = 4;

    private Masking() {
    }

    static String lastFour(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        // A value no longer than the visible suffix would otherwise show entirely in the
        // clear (e.g. a 4-digit legacy-style security code) - mask it completely instead.
        if (value.length() <= VISIBLE_SUFFIX_LENGTH) {
            return "*".repeat(value.length());
        }
        String tail = value.substring(value.length() - VISIBLE_SUFFIX_LENGTH);
        return "*".repeat(value.length() - VISIBLE_SUFFIX_LENGTH) + tail;
    }
}
