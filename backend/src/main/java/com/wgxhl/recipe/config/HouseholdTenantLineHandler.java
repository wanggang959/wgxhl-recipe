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
            "household_member"
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
