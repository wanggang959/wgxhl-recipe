package com.wgxhl.recipe.config;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class HouseholdTenantLineHandler implements TenantLineHandler {

    private static final Set<String> GLOBAL_TABLES = new HashSet<>(Arrays.asList(
            "app_user",
            "household",
            "household_member",
            "household_invite",
            "email_verification_code",
            "account_security_ticket",
            "email_send_quota",
            "mail_message_log",
            "mail_delivery_event",
            "recipe",
            "recipe_image",
            "recipe_step",
            "recipe_ingredient_rel",
            "recipe_seasoning_rel",
            "recipe_category",
            "recipe_like",
            "ingredient",
            "seasoning"
    ));

    @Override
    public Expression getTenantId() {
        return new StringValue(TenantContext.require());
    }

    @Override
    public String getTenantIdColumn() {
        return "household_id";
    }

    @Override
    public boolean ignoreTable(String tableName) {
        return tableName == null || GLOBAL_TABLES.contains(tableName.toLowerCase(Locale.ROOT));
    }
}
