package com.wgxhl.recipe.ledger.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.config.TenantContext;
import com.wgxhl.recipe.config.UserContext;
import com.wgxhl.recipe.household.entity.HouseholdMember;
import com.wgxhl.recipe.household.mapper.HouseholdMemberMapper;
import com.wgxhl.recipe.ledger.dto.LedgerPageQuery;
import com.wgxhl.recipe.ledger.entity.LedgerEntry;
import com.wgxhl.recipe.ledger.mapper.LedgerEntryMapper;
import com.wgxhl.recipe.ledger.mapper.TodoLedgerSettingMapper;
import com.wgxhl.recipe.ledger.entity.TodoLedgerSetting;
import com.wgxhl.recipe.todo.entity.Todo;
import com.wgxhl.recipe.expense.entity.SpecialExpenseItem;
import com.wgxhl.recipe.ledger.dto.CompleteWithLedgerRequest;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;

import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class LedgerService {
    private final LedgerEntryMapper entries;
    private final HouseholdMemberMapper members;
    private final JdbcTemplate db;
    private final TodoLedgerSettingMapper settings;

    public LedgerService(LedgerEntryMapper entries, HouseholdMemberMapper members, JdbcTemplate db, TodoLedgerSettingMapper settings) {
        this.entries = entries;
        this.members = members;
        this.db = db;
        this.settings = settings;
    }

    public String validateSetting(TodoLedgerSetting setting) {
        if (setting == null || !Boolean.TRUE.equals(setting.getEnabled())) return null;
        if (!StringUtils.hasText(setting.getCategory()) || setting.getCategory().trim().length() > 64) return "请选择账单分类";
        if (!StringUtils.hasText(setting.getDescription()) || setting.getDescription().trim().length() > 200) return "请输入账单内容";
        if (setting.getEstimatedAmount() != null && (setting.getEstimatedAmount().signum() <= 0 || setting.getEstimatedAmount().scale() > 2 || setting.getEstimatedAmount().precision() - setting.getEstimatedAmount().scale() > 10)) return "预估金额须大于零且最多两位小数";
        return null;
    }

    public void saveSetting(String todoId, TodoLedgerSetting value) {
        if (value == null) return;
        TodoLedgerSetting current = settings.selectById(todoId);
        boolean isNew = current == null;
        if (isNew) { current = new TodoLedgerSetting(); current.setTodoId(todoId); current.setCreateTime(LocalDateTime.now()); }
        current.setEnabled(Boolean.TRUE.equals(value.getEnabled()));
        current.setCategory(value.getCategory());
        current.setDescription(value.getDescription());
        current.setEstimatedAmount(value.getEstimatedAmount());
        current.setUpdateTime(LocalDateTime.now());
        if (isNew) settings.insert(current);
        else settings.update(null, new LambdaUpdateWrapper<TodoLedgerSetting>().eq(TodoLedgerSetting::getTodoId, todoId)
                .set(TodoLedgerSetting::getEnabled, current.getEnabled()).set(TodoLedgerSetting::getCategory, current.getCategory())
                .set(TodoLedgerSetting::getDescription, current.getDescription()).set(TodoLedgerSetting::getEstimatedAmount, current.getEstimatedAmount())
                .set(TodoLedgerSetting::getUpdateTime, current.getUpdateTime()));
    }

    public TodoLedgerSetting setting(String todoId) { return settings.selectById(todoId); }
    public void deleteSetting(String todoId) { settings.deleteById(todoId); }
    public void deleteHouseholdData() {
        settings.delete(new LambdaQueryWrapper<>());
        entries.delete(new LambdaQueryWrapper<>());
    }

    public ApiResponse<LedgerEntry> prepareTodoRecord(Todo todo, CompleteWithLedgerRequest request) {
        TodoLedgerSetting preset = setting(todo.getId());
        if (preset == null || !Boolean.TRUE.equals(preset.getEnabled())) return ApiResponse.fail("此待办未开启记账");
        if (todo.getDueTime() == null || !todo.getDueTime().equals(request.getExpectedDueTime())) return ApiResponse.fail("待办周期已变化，请刷新后重试");
        LedgerEntry entry = new LedgerEntry();
        entry.setEntryType("EXPENSE");
        entry.setCategory(preset.getCategory());
        entry.setDescription(preset.getDescription());
        entry.setAmount(request.getActualAmount());
        entry.setOccurredOn(LocalDate.now());
        entry.setPayerId(request.getPayerId());
        String error = validate(entry);
        if (error != null) return ApiResponse.fail(error);
        boolean expense = "SPECIAL_EXPENSE_ITEM".equals(todo.getRelatedType()) && StringUtils.hasText(todo.getRelatedId());
        entry.setSourceType("TODO");
        entry.setSourceId(todo.getId());
        entry.setSourceCycle(todo.getDueTime().toString());
        if (expense) entry.setRelatedExpenseId(todo.getRelatedId());
        entry.setSourceCycleLabel(todo.getDueTime().toLocalDate().toString());
        Long count = entries.selectCount(new LambdaQueryWrapper<LedgerEntry>()
                .eq(LedgerEntry::getSourceType, entry.getSourceType()).eq(LedgerEntry::getSourceId, entry.getSourceId())
                .eq(LedgerEntry::getSourceCycle, entry.getSourceCycle()));
        if (count != null && count > 0) return ApiResponse.fail("该笔支出已有账单，请核对后再操作");
        if (expense && expenseEntry(todo.getRelatedId()) != null) return ApiResponse.fail("该专项开支已有账单，请核对后再操作");
        entry.setId(IdUtil.simpleUUID());
        entry.setCreatedBy(UserContext.requireUserId());
        entry.setCreateTime(LocalDateTime.now());
        entry.setUpdateTime(entry.getCreateTime());
        return ApiResponse.success(entry);
    }

    public void insertPrepared(LedgerEntry entry) { entries.insert(entry); }

    public LedgerEntry expenseEntry(String itemId) {
        if (!StringUtils.hasText(itemId)) return null;
        return entries.selectOne(new LambdaQueryWrapper<LedgerEntry>().eq(LedgerEntry::getRelatedExpenseId, itemId));
    }

    public LedgerEntry prepareExpense(SpecialExpenseItem item) {
        LedgerEntry entry = new LedgerEntry();
        entry.setEntryType("EXPENSE");
        entry.setCategory("购物");
        entry.setDescription(item.getItemName());
        entry.setAmount(item.getActualAmount());
        entry.setPayerId(item.getPayerId());
        entry.setOccurredOn(item.getPurchaseDate() == null ? LocalDate.now() : item.getPurchaseDate());
        entry.setRemark(item.getRemark());
        return entry;
    }

    public void syncExpense(String itemId, LedgerEntry input, LedgerEntry existing) {
        LedgerEntry entry = existing == null ? new LedgerEntry() : existing;
        if (existing == null) {
            entry.setId(IdUtil.simpleUUID());
            entry.setCreatedBy(UserContext.requireUserId());
            entry.setCreateTime(LocalDateTime.now());
            entry.setSourceType("EXPENSE");
            entry.setSourceId(itemId);
            entry.setSourceCycle("once");
            entry.setRelatedExpenseId(itemId);
        }
        applyEditable(entry, input);
        entry.setDeleted(false);
        entry.setUpdateTime(LocalDateTime.now());
        if (existing == null) entries.insert(entry); else entries.updateById(entry);
    }

    public java.util.Set<String> linkedExpenseIds(java.util.List<String> ids) {
        if (ids.isEmpty()) return java.util.Collections.emptySet();
        return entries.selectList(new LambdaQueryWrapper<LedgerEntry>().in(LedgerEntry::getRelatedExpenseId, ids))
                .stream().map(LedgerEntry::getRelatedExpenseId).collect(java.util.stream.Collectors.toSet());
    }

    public java.util.Set<String> deletedExpenseIds(java.util.List<String> ids) {
        if (ids.isEmpty()) return java.util.Collections.emptySet();
        return entries.selectList(new LambdaQueryWrapper<LedgerEntry>().in(LedgerEntry::getRelatedExpenseId, ids).eq(LedgerEntry::getDeleted, true))
                .stream().map(LedgerEntry::getRelatedExpenseId).collect(java.util.stream.Collectors.toSet());
    }

    public void enrich(java.util.List<Todo> todos) {
        if (todos.isEmpty()) return;
        java.util.List<String> ids = todos.stream().map(Todo::getId).collect(java.util.stream.Collectors.toList());
        Map<String, TodoLedgerSetting> byTodo = settings.selectList(new LambdaQueryWrapper<TodoLedgerSetting>().in(TodoLedgerSetting::getTodoId, ids))
                .stream().collect(java.util.stream.Collectors.toMap(TodoLedgerSetting::getTodoId, x -> x));
        todos.forEach(todo -> todo.setLedgerSetting(byTodo.get(todo.getId())));
    }

    public ApiResponse<Map<String, Object>> summary(String value) {
        if (UserContext.isGuest()) return ApiResponse.fail(403, "游客不能查看家庭账本");
        YearMonth month;
        try { month = YearMonth.parse(value); }
        catch (DateTimeParseException | NullPointerException e) { return ApiResponse.fail("请选择有效月份"); }
        String household = TenantContext.require();
        LocalDate start = month.atDay(1), end = month.plusMonths(1).atDay(1);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totals", db.queryForList("select entry_type, coalesce(sum(amount), 0) amount from household_ledger_entry where household_id=? and deleted=0 and occurred_on>=? and occurred_on<? group by entry_type", household, start, end));
        result.put("categories", db.queryForList("select category, sum(amount) amount from household_ledger_entry where household_id=? and deleted=0 and occurred_on>=? and occurred_on<? and entry_type='EXPENSE' group by category order by amount desc", household, start, end));
        return ApiResponse.success(result);
    }

    public ApiResponse<Page<LedgerEntry>> page(LedgerPageQuery request) {
        if (UserContext.isGuest()) return ApiResponse.fail(403, "游客不能查看家庭账本");
        YearMonth month;
        try { month = request.isAllMonths() && StringUtils.hasText(request.getSourceTodoId()) ? null : YearMonth.parse(request.getMonth()); }
        catch (DateTimeParseException | NullPointerException e) { return ApiResponse.fail("请选择有效月份"); }
        long current = Math.max(1, request.getCurrent());
        long size = Math.min(50, Math.max(1, request.getSize()));
        LambdaQueryWrapper<LedgerEntry> query = new LambdaQueryWrapper<LedgerEntry>()
                .eq(LedgerEntry::getDeleted, false)
                .ge(month != null, LedgerEntry::getOccurredOn, month == null ? null : month.atDay(1))
                .lt(month != null, LedgerEntry::getOccurredOn, month == null ? null : month.plusMonths(1).atDay(1))
                .eq(StringUtils.hasText(request.getSourceTodoId()), LedgerEntry::getSourceType, "TODO")
                .eq(StringUtils.hasText(request.getSourceTodoId()), LedgerEntry::getSourceId, request.getSourceTodoId())
                .eq(StringUtils.hasText(request.getEntryType()), LedgerEntry::getEntryType, request.getEntryType())
                .eq(StringUtils.hasText(request.getCategory()), LedgerEntry::getCategory, request.getCategory())
                .eq(StringUtils.hasText(request.getPayerId()), LedgerEntry::getPayerId, request.getPayerId())
                .orderByDesc(LedgerEntry::getOccurredOn, LedgerEntry::getCreateTime, LedgerEntry::getId);
        return ApiResponse.success(entries.selectPage(new Page<>(current, size), query));
    }

    public ApiResponse<LedgerEntry> createManual(LedgerEntry input) {
        if (UserContext.isGuest()) return ApiResponse.fail(403, "游客不能记账");
        String error = validate(input);
        if (error != null) return ApiResponse.fail(error);
        LedgerEntry entry = new LedgerEntry();
        entry.setId(IdUtil.simpleUUID());
        entry.setCreatedBy(UserContext.requireUserId());
        applyEditable(entry, input);
        LocalDateTime now = LocalDateTime.now();
        entry.setCreateTime(now);
        entry.setUpdateTime(now);
        entries.insert(entry);
        return ApiResponse.success(entry);
    }

    public ApiResponse<Void> update(LedgerEntry input) {
        if (UserContext.isGuest()) return ApiResponse.fail(403, "游客不能修改账单");
        LedgerEntry entry = StringUtils.hasText(input.getId()) ? entries.selectById(input.getId()) : null;
        if (entry == null) return ApiResponse.fail("账单不存在");
        if (!canManage(entry)) return ApiResponse.fail(403, "只有记录人或家庭管理员可以修改账单");
        if (Boolean.TRUE.equals(entry.getDeleted())) return ApiResponse.fail("账单已删除");
        if (StringUtils.hasText(entry.getRelatedExpenseId())) return ApiResponse.fail("专项关联账单请通过专项开支修改");
        if (StringUtils.hasText(entry.getSourceType()) && !"EXPENSE".equals(input.getEntryType())) return ApiResponse.fail("待办关联账单只能是支出");
        String error = validate(input);
        if (error != null) return ApiResponse.fail(error);
        applyEditable(entry, input);
        entry.setUpdateTime(LocalDateTime.now());
        entries.updateById(entry);
        return ApiResponse.success(null);
    }

    public ApiResponse<Void> delete(String id) {
        if (UserContext.isGuest()) return ApiResponse.fail(403, "游客不能删除账单");
        LedgerEntry entry = StringUtils.hasText(id) ? entries.selectById(id) : null;
        if (entry == null) return ApiResponse.fail("账单不存在");
        if (!canManage(entry)) return ApiResponse.fail(403, "只有记录人或家庭管理员可以删除账单");
        if (Boolean.TRUE.equals(entry.getDeleted())) return ApiResponse.fail("账单已删除");
        if (StringUtils.hasText(entry.getSourceType())) {
            entry.setDeleted(true);
            entry.setUpdateTime(LocalDateTime.now());
            entries.updateById(entry);
        } else entries.deleteById(id);
        return ApiResponse.success(null);
    }

    private boolean canManage(LedgerEntry entry) {
        return UserContext.isHouseholdManager() || UserContext.requireUserId().equals(entry.getCreatedBy());
    }

    public String validate(LedgerEntry input) {
        if (input == null || !("EXPENSE".equals(input.getEntryType()) || "INCOME".equals(input.getEntryType()))) return "请选择收入或支出";
        if (!StringUtils.hasText(input.getCategory()) || input.getCategory().trim().length() > 64) return "请选择账单分类";
        if (!StringUtils.hasText(input.getDescription()) || input.getDescription().trim().length() > 200) return "请输入账单内容";
        if (input.getAmount() == null || input.getAmount().signum() <= 0 || input.getAmount().scale() > 2 || input.getAmount().precision() - input.getAmount().scale() > 10) return "金额须大于零且最多两位小数";
        if (input.getOccurredOn() == null) return "请选择日期";
        if (input.getRemark() != null && input.getRemark().length() > 500) return "备注过长";
        if (!StringUtils.hasText(input.getPayerId())) return "请选择家庭成员";
        Long count = members.selectCount(new LambdaQueryWrapper<HouseholdMember>()
                .eq(HouseholdMember::getHouseholdId, TenantContext.require())
                .eq(HouseholdMember::getUserId, input.getPayerId())
                .eq(HouseholdMember::getStatus, "normal"));
        return count == null || count == 0 ? "所选成员不属于当前家庭或已停用" : null;
    }

    private void applyEditable(LedgerEntry entry, LedgerEntry input) {
        entry.setEntryType(input.getEntryType());
        entry.setCategory(input.getCategory().trim());
        entry.setDescription(input.getDescription().trim());
        entry.setAmount(input.getAmount().setScale(2, RoundingMode.UNNECESSARY));
        entry.setOccurredOn(input.getOccurredOn());
        entry.setPayerId(input.getPayerId());
        entry.setRemark(input.getRemark());
    }
}
