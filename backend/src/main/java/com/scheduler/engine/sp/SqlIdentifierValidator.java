package com.scheduler.engine.sp;

import java.util.regex.Pattern;

/**
 * sp_name is concatenated directly into the EXEC SQL text in StoredProcedureInvoker (the
 * bound parameters are safely parameterized, but a stored procedure name can't be - JDBC
 * has no placeholder syntax for "the procedure to call"). PLAN.md §4.1 treats sp_name as a
 * trusted DB object identifier, not free text, so this enforces that: only characters legal
 * in a (possibly schema-qualified, possibly bracket-quoted) SQL Server identifier are
 * allowed - no semicolons, whitespace, quotes, or comment tokens that could break out of
 * the EXEC statement.
 */
public final class SqlIdentifierValidator {

    private static final Pattern VALID_IDENTIFIER = Pattern.compile("^[A-Za-z0-9_.\\[\\]]+$");

    private SqlIdentifierValidator() {
    }

    public static void validate(String spName) {
        if (spName == null || spName.isBlank() || !VALID_IDENTIFIER.matcher(spName).matches()) {
            throw new IllegalArgumentException(
                    "sp_name must be a valid SQL Server identifier (letters, digits, '_', '.', '[', ']' only): " + spName);
        }
    }
}
