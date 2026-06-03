package com.wgxhl.recipe.user.util;

import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public final class DefaultAvatars {

    private static final Pattern BUILTIN_AVATAR = Pattern.compile("^/avatars/\\d{2}\\.png$");

    private static final List<String> PATHS = Arrays.asList(
            "/avatars/01.png", "/avatars/02.png", "/avatars/03.png", "/avatars/04.png",
            "/avatars/05.png", "/avatars/06.png", "/avatars/07.png", "/avatars/08.png",
            "/avatars/09.png", "/avatars/10.png", "/avatars/11.png", "/avatars/12.png",
            "/avatars/13.png", "/avatars/14.png", "/avatars/15.png"
    );

    /** 合图第三行第一格为厨师形象，用作王师傅默认头像 */
    public static final String ADMIN_AVATAR = "/avatars/11.png";
    public static final String GUEST_AVATAR = "/avatars/15.png";

    private DefaultAvatars() {
    }

    public static String pickForSeed(String seed) {
        String key = StringUtils.hasText(seed) ? seed : "guest";
        int index = Math.floorMod(key.hashCode(), PATHS.size());
        return PATHS.get(index);
    }

    public static boolean isAllowed(String avatar) {
        return !StringUtils.hasText(avatar) || BUILTIN_AVATAR.matcher(avatar.trim()).matches();
    }

    public static String normalizeOrPick(String avatar, String seed) {
        if (StringUtils.hasText(avatar) && isAllowed(avatar)) {
            return avatar.trim();
        }
        return pickForSeed(seed);
    }
}
