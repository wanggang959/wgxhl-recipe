package com.wgxhl.recipe.common;

import cn.hutool.core.util.IdUtil;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

public final class EntityHelper {

    private EntityHelper() {
    }

    public static void fillId(String id, java.util.function.Consumer<String> idSetter) {
        if (!StringUtils.hasText(id)) {
            idSetter.accept(IdUtil.simpleUUID());
        }
    }

    public static void fillCreateTime(LocalDateTime createTime, LocalDateTime updateTime,
                                      java.util.function.Consumer<LocalDateTime> createSetter,
                                      java.util.function.Consumer<LocalDateTime> updateSetter) {
        LocalDateTime now = LocalDateTime.now();
        if (createTime == null) {
            createSetter.accept(now);
        }
        if (updateTime == null) {
            updateSetter.accept(now);
        }
    }

    public static LocalDateTime now() {
        return LocalDateTime.now();
    }
}
