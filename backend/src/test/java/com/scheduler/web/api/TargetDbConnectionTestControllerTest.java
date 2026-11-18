package com.scheduler.web.api;

import com.scheduler.domain.config.TargetDbConfig;
import com.scheduler.domain.config.TargetDbConfigRepository;
import com.scheduler.infra.persistence.jdbc.AbstractRepositoryIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PLAN.md §6 (2026-11-18)/TASKS.md T28 DoD. The "success" cases connect to the same
 * MSSQLServerContainer that AbstractRepositoryIntegrationTest already boots as the meta DB -
 * it's a real, reachable SQL Server, so pointing TargetDbConnectionTester at it is a genuine
 * end-to-end connection test, not a mock.
 */
@AutoConfigureMockMvc
@WithMockUser(username = "test-admin", roles = "ADMIN")
class TargetDbConnectionTestControllerTest extends AbstractRepositoryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TargetDbConfigRepository targetDbConfigRepository;

    // Same meta-DB MSSQLServerContainer AbstractRepositoryIntegrationTest already boots,
    // injected back via the DB_URL/DB_USERNAME/DB_PASSWORD properties it registers - the
    // container's static field itself is package-private to infra.persistence.jdbc, so this
    // test (in web.api) goes through Spring config instead of touching it directly.
    @Value("${DB_URL}")
    private String containerJdbcUrl;

    @Value("${DB_USERNAME}")
    private String containerUsername;

    @Value("${DB_PASSWORD}")
    private String containerPassword;

    @Test
    void unsavedInputThatActuallyConnectsReturnsSuccess() throws Exception {
        mockMvc.perform(post("/api/config/target-db/test-connection")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jdbcUrl":"%s","username":"%s","password":"%s"}
                                """.formatted(containerJdbcUrl, containerUsername, containerPassword)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void savedConfigThatActuallyConnectsReturnsSuccess() throws Exception {
        TargetDbConfig saved = targetDbConfigRepository.save(new TargetDbConfig(
                null, "Reachable test target", containerJdbcUrl, containerUsername, containerPassword, null, null
        ));

        mockMvc.perform(post("/api/config/target-db/" + saved.id() + "/test-connection"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void wrongPasswordFailsWithoutLeakingItInTheResponse() throws Exception {
        String wrongPassword = "definitely-not-the-real-password";

        String response = mockMvc.perform(post("/api/config/target-db/test-connection")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jdbcUrl":"%s","username":"%s","password":"%s"}
                                """.formatted(containerJdbcUrl, containerUsername, wrongPassword)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain(wrongPassword);
        assertThat(response).doesNotContain(containerPassword);
    }

    /**
     * A plain TCP "black hole" (accepts the connection, then never sends a single byte back) is
     * used instead of a reserved/unroutable IP - an unroutable address can fail near-instantly
     * via an OS-level ICMP/route rejection (observed: ~0.5s against 203.0.113.1 in this
     * environment), which would make this test pass even if TargetDbConnectionTester never
     * called DriverManager.setLoginTimeout at all. A held-open socket that never answers the
     * TDS pre-login handshake forces the driver to actually wait out the login timeout, so the
     * measured elapsed time (bounded both above AND below 5s) is real evidence the 5s cap in
     * TargetDbConnectionTester, not an unrelated fast/slow failure, is what ended the attempt.
     */
    @Test
    void unreachableHostFailsWithinEnforcedTimeout() throws Exception {
        try (ServerSocket blackHole = new ServerSocket(0)) {
            Thread acceptThread = new Thread(() -> acceptAndHoldForever(blackHole));
            acceptThread.setDaemon(true);
            acceptThread.start();

            Instant start = Instant.now();

            String response = mockMvc.perform(post("/api/config/target-db/test-connection")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"jdbcUrl":"jdbc:sqlserver://localhost:%d;trustServerCertificate=true;","username":"u","password":"p"}
                                    """.formatted(blackHole.getLocalPort())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(false))
                    .andReturn().getResponse().getContentAsString();

            Duration elapsed = Duration.between(start, Instant.now());
            // Lower bound rules out "failed near-instantly for an unrelated reason"; upper bound
            // rules out "hung past the 5s cap and only failed via some much longer OS default".
            assertThat(elapsed).isGreaterThan(Duration.ofSeconds(4));
            assertThat(elapsed).isLessThan(Duration.ofSeconds(15));
            assertThat(response).doesNotContain(String.valueOf(blackHole.getLocalPort()));
        }
    }

    private static void acceptAndHoldForever(ServerSocket serverSocket) {
        try (Socket client = serverSocket.accept()) {
            Thread.sleep(20_000);
        } catch (IOException | InterruptedException e) {
            // Server socket closed (test method returning) or JVM shutting down - expected.
        }
    }

    /**
     * Real repro, not a crafted message: MSSQLServerContainer auto-sets "encrypt=false" unless
     * the caller requests encryption explicitly (confirmed by decompiling
     * MSSQLServerContainer.getJdbcUrl() - it checks urlParameters.noneMatch(... "encrypt" ...)
     * and defaults to "false"), which is why simply dropping ";trustServerCertificate=true"
     * still connected fine in an earlier version of this test - no TLS negotiation happens at
     * all with encrypt=false. Forcing ";encrypt=true;" (without trustServerCertificate) turns
     * TLS validation back on against the container's self-signed cert, reproducing mssql-jdbc's
     * genuine PKIX/SSLHandshakeException path - exactly the case a user hit against a real
     * target DB with encrypt=true and no valid cert.
     */
    @Test
    void sslCertificateFailureGetsAnSslSpecificMessageNotTheGenericConnectFailureMessage() throws Exception {
        String urlWithUntrustedEncryption = containerJdbcUrl.replace(";trustServerCertificate=true;", ";encrypt=true;");

        String response = mockMvc.perform(post("/api/config/target-db/test-connection")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jdbcUrl":"%s","username":"%s","password":"%s"}
                                """.formatted(urlWithUntrustedEncryption, containerUsername, containerPassword)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andReturn().getResponse().getContentAsString();

        assertThat(response).contains("SSL");
        assertThat(response).contains("encrypt=false;trustServerCertificate=true;");
        // The real bug this was fixed for: the SSL message contains the word "connection" (from
        // "...establish a secure connection..."), which would wrongly match the generic
        // tcp-ip/"connect" category's keyword if that category were checked first.
        assertThat(response).doesNotContain("서버에 연결할 수 없습니다");
    }

    /**
     * Regression guard for the three categories that existed before the SSL category was added
     * above - login-failed (wrongPasswordFailsWithoutLeakingItInTheResponse) and timeout
     * (unreachableHostFailsWithinEnforcedTimeout) are covered by the existing tests in this
     * class; this covers the third ("연결불가"/connection-refused) category, which a closed
     * local port reproduces deterministically and near-instantly (no SSL keywords appear in a
     * plain "Connection refused" message, so it must still fall through to this category).
     */
    @Test
    void connectionRefusedStillMapsToTheGenericUnreachableCategory() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }

        String response = mockMvc.perform(post("/api/config/target-db/test-connection")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jdbcUrl":"jdbc:sqlserver://localhost:%d;trustServerCertificate=true;","username":"u","password":"p"}
                                """.formatted(closedPort)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andReturn().getResponse().getContentAsString();

        assertThat(response).contains("서버에 연결할 수 없습니다");
    }
}
