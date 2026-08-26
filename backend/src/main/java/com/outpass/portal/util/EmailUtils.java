package com.outpass.portal.util;

import java.util.Locale;

// Single place email normalization happens, so every account table (Student, Warden,
// SecurityGuard, Admin) stores and compares emails the same way. "Test@gmail.com" and
// "test@gmail.com" must be treated as the same account across all four tables.
public final class EmailUtils {

    private EmailUtils() {
    }

    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
