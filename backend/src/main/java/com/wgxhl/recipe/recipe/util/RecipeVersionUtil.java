package com.wgxhl.recipe.recipe.util;

import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

public final class RecipeVersionUtil {

    public static final String DEFAULT_VERSION = "1.0";

    private static final Pattern VERSION_PATTERN = Pattern.compile("^\\d{1,2}\\.\\d{1,2}$");

    private RecipeVersionUtil() {
    }

    public static String normalize(String version) {
        if (!StringUtils.hasText(version)) {
            return DEFAULT_VERSION;
        }
        return version.trim();
    }

    public static boolean isValid(String version) {
        return StringUtils.hasText(version) && VERSION_PATTERN.matcher(version.trim()).matches();
    }
}
