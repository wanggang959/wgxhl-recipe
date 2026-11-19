package com.wgxhl.recipe.vehicle;

import cn.hutool.core.util.IdUtil;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.config.TenantContext;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/vehicle/expense-types")
public class VehicleExpenseTypes {
    private static final List<Type> DEFAULTS = Arrays.asList(
            new Type("driving", "行车费", "#FA6356", true),
            new Type("maintenance", "维修保养", "#F8BF19", true),
            new Type("insurance", "保险和交通规费", "#DC358A", true),
            new Type("wash", "洗车", "#24BF63", true),
            new Type("other", "其他", "#88978F", true));
    private final JdbcTemplate db;

    public VehicleExpenseTypes(JdbcTemplate db) {
        this.db = db;
    }

    @PostMapping("/list")
    public ApiResponse<List<Type>> list() {
        return ApiResponse.success(types());
    }

    List<Type> types() {
        List<Type> saved = db.query(
                "SELECT type_key, name, color, enabled FROM vehicle_expense_type WHERE household_id=? ORDER BY enabled DESC, name",
                (rs, i) -> new Type(rs.getString(1), rs.getString(2), rs.getString(3), rs.getBoolean(4)), TenantContext.require());
        List<Type> types = DEFAULTS.stream().map(base -> saved.stream().filter(row -> base.getKey().equals(row.getKey()))
                .findFirst().orElse(base)).collect(Collectors.toList());
        saved.stream().filter(row -> DEFAULTS.stream().noneMatch(base -> base.getKey().equals(row.getKey()))).forEach(types::add);
        return types;
    }

    Type enabled(String key) {
        return types().stream().filter(row -> row.isEnabled() && row.getKey().equals(key)).findFirst().orElse(null);
    }

    @PostMapping("/save")
    public ApiResponse<List<Type>> save(@RequestBody Type input) {
        String name = input.getName() == null ? "" : input.getName().trim();
        if (name.isEmpty() || name.length() > 80 || "油费".equals(name)
                || input.getColor() == null || !input.getColor().matches("#[0-9a-fA-F]{6}")) {
            return ApiResponse.fail("请填写有效的类型名称和颜色，“油费”由加油记录自动统计");
        }
        List<Type> current = types();
        String key = input.getKey();
        boolean create = key == null || key.isEmpty();
        if (!create && current.stream().noneMatch(row -> row.getKey().equals(key))) return ApiResponse.fail("费用类型不存在");
        if (current.stream().anyMatch(row -> !row.getKey().equals(key) && row.getName().equalsIgnoreCase(name))) {
            return ApiResponse.fail("费用类型名称已存在");
        }
        if (create && current.size() >= 100) return ApiResponse.fail("最多设置 100 种费用类型");

        try {
            String home = TenantContext.require();
            int updated = create ? 0 : db.update(
                    "UPDATE vehicle_expense_type SET name=?, color=?, enabled=? WHERE household_id=? AND type_key=?",
                    name, input.getColor(), input.isEnabled(), home, key);
            if (updated == 0 && (create || db.queryForObject(
                    "SELECT COUNT(*) FROM vehicle_expense_type WHERE household_id=? AND type_key=?",
                    Integer.class, home, key) == 0)) db.update(
                    "INSERT INTO vehicle_expense_type (household_id,type_key,name,color,enabled) VALUES (?,?,?,?,?)",
                    home, create ? IdUtil.simpleUUID() : key, name, input.getColor(), create || input.isEnabled());
            return list();
        } catch (DuplicateKeyException ex) {
            return ApiResponse.fail("费用类型已存在或已被更新，请刷新后重试");
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Type {
        private String key;
        private String name;
        private String color;
        private boolean enabled = true;
    }
}
