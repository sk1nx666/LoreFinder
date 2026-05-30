package com.lorefinder;

import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts plausible calendar years from sign text (standalone 4-digit runs only).
 */
public final class SignYearParser {
    /** 2b2t was founded in 2010 — years before this are ignored on signs. */
    public static final int MIN_YEAR = 2010;

    /** Exactly four digits, not part of a longer number (avoids dates like 05/29/2026 → 05). */
    private static final Pattern FOUR_DIGIT_YEAR = Pattern.compile("(?<!\\d)(\\d{4})(?!\\d)");

    private SignYearParser() {}

    public static int maxYear() {
        return Year.now().getValue();
    }

    public static boolean isPlausibleYear(int year) {
        return year >= MIN_YEAR && year <= maxYear();
    }

    public static List<Integer> parseYears(String text) {
        List<Integer> years = new ArrayList<>();
        if (text == null || text.isEmpty()) return years;

        Matcher matcher = FOUR_DIGIT_YEAR.matcher(text);
        while (matcher.find()) {
            int year = Integer.parseInt(matcher.group(1));
            if (isPlausibleYear(year)) {
                years.add(year);
            }
        }

        return years;
    }

    public enum CompareMode {
        /** Any parsed year &lt; threshold (e.g. &quot;&lt;2025&quot;). */
        AnyBefore,
        /** Any parsed year &lt;= threshold. */
        AnyBeforeOrEqual,
        /** Newest year on the sign &lt; threshold. */
        MaxBefore,
        /** Newest year on the sign &lt;= threshold. */
        MaxBeforeOrEqual,
        /** Any parsed year &gt; threshold. */
        AnyAfter,
        /** Any parsed year &gt;= threshold. */
        AnyAfterOrEqual
    }

    public static boolean matches(List<Integer> years, int threshold, CompareMode mode) {
        if (years.isEmpty()) return false;

        return switch (mode) {
            case AnyBefore -> years.stream().anyMatch(y -> y < threshold);
            case AnyBeforeOrEqual -> years.stream().anyMatch(y -> y <= threshold);
            case MaxBefore -> years.stream().max(Integer::compareTo).orElse(threshold) < threshold;
            case MaxBeforeOrEqual -> years.stream().max(Integer::compareTo).orElse(threshold) <= threshold;
            case AnyAfter -> years.stream().anyMatch(y -> y > threshold);
            case AnyAfterOrEqual -> years.stream().anyMatch(y -> y >= threshold);
        };
    }
}
