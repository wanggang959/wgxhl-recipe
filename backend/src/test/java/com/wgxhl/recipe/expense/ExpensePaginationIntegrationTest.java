package com.wgxhl.recipe.expense;

import com.wgxhl.recipe.config.TenantContext;
import com.wgxhl.recipe.expense.dto.SpecialExpenseItemPageDTO;
import com.wgxhl.recipe.expense.entity.SpecialExpenseItem;
import com.wgxhl.recipe.expense.service.SpecialExpenseItemService;
import com.wgxhl.recipe.user.entity.AppUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("integration")
@EnabledIfSystemProperty(named = "test.integration.enabled", matches = "true")
@Transactional
class ExpensePaginationIntegrationTest {
    @Autowired private JdbcTemplate db;
    @Autowired private SpecialExpenseItemService service;
    @AfterEach void clearTenant() { TenantContext.clear(); }

    @Test
    void completedItemsRemainAfterPendingItemsAcrossPages() {
        String household = "00000000-0000-0000-0000-000000000001";
        String project = UUID.randomUUID().toString();
        for (int i = 0; i < 52; i++) {
            db.update("insert into special_expense_item (id, household_id, project_id, item_name, status) values (?, ?, ?, ?, ?)",
                    UUID.randomUUID().toString(), household, project, "test-" + i, i == 0 ? "DONE" : "TODO");
        }
        TenantContext.set(household);
        AppUser actor = new AppUser(); actor.setId("admin-wangshifu");
        SpecialExpenseItemPageDTO query = new SpecialExpenseItemPageDTO();
        query.setProjectId(project); query.setSize(500L); query.setCurrent(1L);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<SpecialExpenseItem> first = service.page(query, actor).getData();
        assertEquals(50, first.getRecords().size());
        assertTrue(first.getRecords().stream().noneMatch(item -> "DONE".equals(item.getStatus())));
        query.setCurrent(2L);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<SpecialExpenseItem> second = service.page(query, actor).getData();
        assertEquals(2, second.getRecords().size());
        assertEquals("DONE", second.getRecords().get(1).getStatus());
    }
}
