package com.wgxhl.recipe.user.util;

import java.util.regex.Pattern;

public final class EmailAddress {
    private static final Pattern FORMAT = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private EmailAddress() {}

    public static boolean isValid(String email) {
        return email != null && email.length() <= 200 && FORMAT.matcher(email).matches();
    }
}
