package com.scheduler.infra.db;

import com.scheduler.web.api.dto.ConnectionTestResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

/**
 * Shared by both target-db test-connection endpoints (PLAN.md §6, 2026-11-18 addition) - a
 * single connection-pool-free DriverManager probe + SELECT 1 + close, with a short timeout so
 * an unreachable/dead target DB can't make the admin UI hang (same spirit as §3.1b's "one slow
 * target DB shouldn't block the others", applied here to "one slow target DB shouldn't block
 * this API call").
 */
@Component
public class TargetDbConnectionTester {

    private static final Logger log = LoggerFactory.getLogger(TargetDbConnectionTester.class);
    private static final int LOGIN_TIMEOUT_SECONDS = 5;
    private static final int MIN_REDACTABLE_PASSWORD_LENGTH = 4;

    public ConnectionTestResponse test(String jdbcUrl, String username, String password) {
        // DriverManager.setLoginTimeout is JVM-global, not per-connection - restore the
        // previous value afterwards so this call doesn't leave a short timeout in effect for
        // unrelated DriverManager.getConnection() callers (e.g. StoredProcedureInvoker).
        int previousTimeout = DriverManager.getLoginTimeout();
        DriverManager.setLoginTimeout(LOGIN_TIMEOUT_SECONDS);
        try (Connection connection = DriverManager.getConnection(jdbcUrl, connectionProperties(username, password));
             Statement statement = connection.createStatement()) {
            statement.execute("SELECT 1");
            return new ConnectionTestResponse(true, "연결에 성공했습니다.");
        } catch (SQLException e) {
            return new ConnectionTestResponse(false, sanitize(e, password));
        } finally {
            DriverManager.setLoginTimeout(previousTimeout);
        }
    }

    /**
     * DriverManager.setLoginTimeout alone only bounds the initial TCP-connect phase - a target
     * that accepts the TCP connection but then never answers the TDS pre-login/login handshake
     * (e.g. a firewall that allows the connection through but silently drops the traffic) left
     * the driver blocked on a socket read with no timeout at all, measured empirically to hang
     * well past the 5s login timeout. mssql-jdbc's "socketTimeout" connection property (millis)
     * additionally bounds every individual socket read, closing that gap.
     */
    private Properties connectionProperties(String username, String password) {
        Properties props = new Properties();
        props.setProperty("user", username);
        props.setProperty("password", password);
        props.setProperty("socketTimeout", String.valueOf(LOGIN_TIMEOUT_SECONDS * 1000));
        return props;
    }

    /**
     * The mssql-jdbc driver's SQLException messages don't normally echo the password, but a
     * raw driver message is never relayed to the client regardless (it can still contain the
     * host/jdbcUrl) - only a small set of safe, fixed phrases go back in the response. The full
     * exception (stack trace) goes to the server log only, with an extra defensive redaction of
     * the password string in case it ever shows up in a driver message.
     *
     * The redaction only fires for passwords of a few characters or more - blindly replacing a
     * short password (e.g. a 1-2 char test value) would also blot out any ordinary word in the
     * driver's message that happens to contain the same characters (seen in practice: password
     * "p" turned "port"/"properties" into "***ort"/"***ro***erties" in the log).
     */
    private String sanitize(SQLException e, String password) {
        String rawMessage = e.getMessage() == null ? "" : e.getMessage();
        String logSafeMessage = (password != null && password.length() >= MIN_REDACTABLE_PASSWORD_LENGTH)
                ? rawMessage.replace(password, "***")
                : rawMessage;
        log.warn("Target DB connection test failed (sqlState={}): {}", e.getSQLState(), logSafeMessage, e);

        String lower = rawMessage.toLowerCase();
        if (lower.contains("login failed")) {
            return "인증에 실패했습니다. 사용자명 또는 비밀번호를 확인하세요.";
        }
        // Must be checked before the timeout/tcp-ip categories below: mssql-jdbc's SSL/PKIX
        // failure message reads "...could not establish a secure connection to SQL Server by
        // using Secure Sockets Layer (SSL) encryption. Error: "PKIX path building failed: ..."
        // - the word "connection" in there would otherwise match the tcp-ip category's
        // "connect" keyword first and misreport this as a plain network-reachability problem,
        // hiding the actual (and very common) "server has no trusted SSL cert" cause.
        if (lower.contains("pkix") || lower.contains("sslhandshake") || lower.contains("ssl")
                || lower.contains("certificate") || lower.contains("encrypt")) {
            return "서버와 SSL 암호화 연결을 수립할 수 없습니다. 대상 서버에 유효한 SSL 인증서가 없다면, "
                    + "JDBC URL 끝에 ;encrypt=false;trustServerCertificate=true; 를 추가해 보세요.";
        }
        if (lower.contains("timeout") || lower.contains("timed out")) {
            return "연결 시간이 초과되었습니다. 호스트/포트 또는 네트워크 상태를 확인하세요.";
        }
        if (lower.contains("tcp/ip") || lower.contains("connect") || lower.contains("unreachable")
                || lower.contains("host")) {
            return "서버에 연결할 수 없습니다. 호스트/포트를 확인하세요.";
        }
        return "데이터베이스 연결에 실패했습니다.";
    }
}
