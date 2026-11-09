package org.example.auth.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Audit log lines for login attempts, written to a dedicated logger ("auth.audit") so they can be
 * routed, retained or alerted on separately from application logs.

 */
public final class LoginAudit {
    private static final Logger log = LoggerFactory.getLogger("auth.audit");
    private static final int MAX_LENGTH = 64;

    private LoginAudit() {
    }

    public static void loginSucceeded(String username, String clientIp) {
        log.info("event=login_success username=\"{}\" ip={}", sanitize(username), sanitize(clientIp));
    }

    public static void loginFailed(String username, String clientIp, String reason) {
        log.warn("event=login_failed username=\"{}\" ip={} reason={}",
                sanitize(username), sanitize(clientIp), sanitize(reason));
    }

    static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replaceAll("[\\p{Cc}\\u2028\\u2029\"]", "_");
        return cleaned.length() > MAX_LENGTH ? cleaned.substring(0, MAX_LENGTH) + "..." : cleaned;
    }
}