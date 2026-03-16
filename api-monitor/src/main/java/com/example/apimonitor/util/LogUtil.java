package com.example.apimonitor.util;
/**
 * Shared utility helpers for safe log output.
 */
public final class LogUtil {

    private LogUtil() {}
    /**
     * Strips CR/LF from a value before writing it to a log entry, preventing
     * log-injection attacks where a crafted URL path or name containing newline
     * sequences could forge additional log lines.
     */
    public static String sanitize(String value) {
        return (value == null) ? null : value.replace('\n', ' ').replace('\r', ' ');
    }
}
