package com.scheduler.engine.sp;

import org.springframework.stereotype.Component;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generalizes the legacy executeTask1() pattern (Class.forName + DriverManager.getConnection
 * + "EXEC spName") - same concept, but the target DB connection info and SP name/params come
 * from schedule_definition/target_db_config (via TargetDbConfig/ScheduleDefinition) instead
 * of Config.java constants, and both parameterless and parameterized EXEC calls are
 * supported (PLAN.md §4.2/§8).
 */
@Component
public class StoredProcedureInvoker {

    public List<Map<String, Object>> invoke(String jdbcUrl, String username, String password,
                                              String spName, List<Object> boundParams) {
        // sp_name can't be a JDBC bind parameter (there's no placeholder syntax for "which
        // procedure to call"), so it's validated as a trusted identifier instead of being
        // concatenated as-is - defense in depth even though callers (ScheduleController)
        // already reject bad sp_name values at the API boundary.
        SqlIdentifierValidator.validate(spName);

        String placeholders = boundParams.isEmpty()
                ? ""
                : " " + String.join(",", boundParams.stream().map(p -> "?").toArray(String[]::new));
        String sql = "EXEC " + spName + placeholders;

        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
             CallableStatement stmt = conn.prepareCall(sql)) {

            for (int i = 0; i < boundParams.size(); i++) {
                stmt.setObject(i + 1, boundParams.get(i));
            }

            try (ResultSet rs = stmt.executeQuery()) {
                return toRows(rs);
            }
        } catch (SQLException e) {
            throw new StoredProcedureExecutionException(
                    "Failed to execute stored procedure " + spName + ": " + e.getMessage(), e);
        }
    }

    private List<Map<String, Object>> toRows(ResultSet rs) throws SQLException {
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();
        List<Map<String, Object>> rows = new ArrayList<>();

        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                row.put(metaData.getColumnLabel(i), rs.getObject(i));
            }
            rows.add(row);
        }
        return rows;
    }
}
