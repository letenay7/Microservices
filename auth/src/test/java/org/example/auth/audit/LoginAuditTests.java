package org.example.auth.audit;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Plain tests of the audit log lines, captured with a Logback ListAppender. */
class LoginAuditTests {

    private final Logger auditLogger = (Logger) LoggerFactory.getLogger("auth.audit");
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        appender = new ListAppender<>();
        appender.start();
        auditLogger.setLevel(Level.INFO);
        auditLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        auditLogger.detachAppender(appender);
    }

    private String onlyMessage() {
        assertEquals(1, appender.list.size(), "exactly one audit line expected");
        return appender.list.getFirst().getFormattedMessage();
    }

    @Test
    void failedLoginShouldBeLoggedAsWarningWithUsernameIpAndReason() {
        LoginAudit.loginFailed("martin", "203.0.113.9", "bad_credentials");

        assertEquals(Level.WARN, appender.list.getFirst().getLevel());
        assertEquals("event=login_failed username=\"martin\" ip=203.0.113.9 reason=bad_credentials", onlyMessage());
    }

    @Test
    void successfulLoginShouldBeLoggedAtInfo() {
        LoginAudit.loginSucceeded("martin", "203.0.113.9");

        assertEquals(Level.INFO, appender.list.getFirst().getLevel());
        assertEquals("event=login_success username=\"martin\" ip=203.0.113.9", onlyMessage());
    }

    @Test
    void lineBreaksInTheUsernameShouldNotBeAbleToForgeLogEntries() {
        String malicious = "martin\r\n2026-11-06 INFO event=login_success username=\"admin\" ip=1.1.1.1";

        LoginAudit.loginFailed(malicious, "203.0.113.9", "bad_credentials");

        String message = onlyMessage();
        assertFalse(message.contains("\r"));
        assertFalse(message.contains("\n"));
    }

    @Test
    void unicodeLineSeparatorsShouldBeNeutralisedToo() {
        LoginAudit.loginFailed("a\u2028b\u2029c\u0085d", "203.0.113.9", "bad_credentials");

        String message = onlyMessage();
        assertFalse(message.contains("\u2028"));
        assertFalse(message.contains("\u2029"));
        assertFalse(message.contains("\u0085"));
    }

    @Test
    void quotesInTheUsernameShouldNotBeAbleToBreakOutOfTheQuotedField() {
        LoginAudit.loginFailed("x\" ip=9.9.9.9 reason=fine \"", "203.0.113.9", "bad_credentials");

        long quotes = onlyMessage().chars().filter(character -> character == '"').count();
        assertEquals(2, quotes, "only the opening and closing quote of the username field may remain");
    }

    @Test
    void veryLongUsernameShouldBeTruncated() {
        LoginAudit.loginFailed("u".repeat(5000), "203.0.113.9", "bad_credentials");

        assertTrue(onlyMessage().length() < 200);
    }

    @Test
    void nullValuesShouldNotThrow() {
        assertDoesNotThrow(
                () -> LoginAudit.loginFailed(null, null, null));
    }
}