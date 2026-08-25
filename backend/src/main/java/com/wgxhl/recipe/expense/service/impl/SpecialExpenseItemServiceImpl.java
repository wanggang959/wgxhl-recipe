package com.wgxhl.recipe.expense.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.expense.dto.SpecialExpenseItemPageDTO;
import com.wgxhl.recipe.expense.entity.SpecialExpenseItem;
import com.wgxhl.recipe.expense.entity.SpecialExpenseProject;
import com.wgxhl.recipe.expense.mapper.SpecialExpenseItemMapper;
import com.wgxhl.recipe.expense.mapper.SpecialExpenseProjectMapper;
import com.wgxhl.recipe.expense.service.SpecialExpenseItemService;
import com.wgxhl.recipe.todo.entity.Todo;
import com.wgxhl.recipe.todo.service.TodoService;
import com.wgxhl.recipe.user.entity.AppUser;
import com.wgxhl.recipe.user.mapper.AppUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SpecialExpenseItemServiceImpl extends ServiceImpl<SpecialExpenseItemMapper, SpecialExpenseItem>
        implements SpecialExpenseItemService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_ORDERED = "ORDERED";
    private static final String STATUS_RECEIVED = "RECEIVED";
    private static final String STATUS_DONE = "DONE";
    private static final String RELATED_TYPE = "SPECIAL_EXPENSE_ITEM";

    private final SpecialExpenseProjectMapper projectMapper;
    private final AppUserMapper appUserMapper;
    private final TodoService todoService;

    public SpecialExpenseItemServiceImpl(SpecialExpenseProjectMapper projectMapper,
                                         AppUserMapper appUserMapper,
                                         TodoService todoService) {
        this.projectMapper = projectMapper;
        this.appUserMapper = appUserMapper;
        this.todoService = todoService;
    }

    @Override
    public boolean save(SpecialExpenseItem entity) {
        if (!StringUtils.hasText(entity.getId())) {
            entity.setId(IdUtil.simpleUUID());
        }
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        normalize(entity);
        return super.save(entity);
    }

    @Override
    public boolean updateById(SpecialExpenseItem entity) {
        entity.setUpdateTime(LocalDateTime.now());
        normalize(entity);
        return super.updateById(entity);
    }

    @Override
    public ApiResponse<Page<SpecialExpenseItem>> page(SpecialExpenseItemPageDTO dto, AppUser actor) {
        Page<SpecialExpenseItem> page = new Page<>(dto.getCurrent(), dto.getSize());
        Page<SpecialExpenseItem> result = lambdaQuery()
                .eq(StringUtils.hasText(dto.getProjectId()), SpecialExpenseItem::getProjectId, dto.getProjectId())
                .and(StringUtils.hasText(dto.getKeyword()), wrapper -> wrapper
                        .like(SpecialExpenseItem::getItemName, dto.getKeyword())
                        .or()
                        .like(SpecialExpenseItem::getRemark, dto.getKeyword()))
                .eq(StringUtils.hasText(dto.getCategory()), SpecialExpenseItem::getCategory, dto.getCategory())
                .eq(StringUtils.hasText(dto.getStatus()), SpecialExpenseItem::getStatus, dto.getStatus())
                .orderByAsc(SpecialExpenseItem::getStatus)
                .orderByAsc(SpecialExpenseItem::getPlannedTime)
                .orderByDesc(SpecialExpenseItem::getCreateTime)
                .page(page);
        enrich(result.getRecords());
        return ApiResponse.success(result);
    }

    @Override
    public ApiResponse<SpecialExpenseItem> detail(String id, AppUser actor) {
        if (!StringUtils.hasText(id)) {
            return ApiResponse.fail("开支项id不能为空");
        }
        SpecialExpenseItem item = super.getById(id);
        if (item == null) {
            return ApiResponse.fail("开支项不存在");
        }
        enrich(Collections.singletonList(item));
        return ApiResponse.success(item);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<SpecialExpenseItem> create(SpecialExpenseItem entity, AppUser actor) {
        if (isGuest(actor)) {
            return ApiResponse.fail("游客不能新增开支项");
        }
        ApiResponse<Void> validate = validate(entity);
        if (validate.getStatus() != 200) {
            return ApiResponse.fail(validate.getMessage());
        }
        save(entity);
        syncTodo(entity, actor);
        enrich(Collections.singletonList(entity));
        return ApiResponse.success("已新增开支项", entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Void> update(SpecialExpenseItem entity, AppUser actor) {
        if (isGuest(actor)) {
            return ApiResponse.fail("游客不能编辑开支项");
        }
        if (!StringUtils.hasText(entity.getId())) {
            return ApiResponse.fail("开支项id不能为空");
        }
        SpecialExpenseItem existing = super.getById(entity.getId());
        if (existing == null) {
            return ApiResponse.fail("开支项不存在");
        }
        ApiResponse<Void> validate = validate(entity);
        if (validate.getStatus() != 200) {
            return validate;
        }
        entity.setTodoId(existing.getTodoId());
        updateById(entity);
        syncTodo(entity, actor);
        return ApiResponse.success("已更新开支项", null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Void> delete(String id, AppUser actor) {
        if (isGuest(actor)) {
            return ApiResponse.fail("游客不能删除开支项");
        }
        if (!StringUtils.hasText(id)) {
            return ApiResponse.fail("开支项id不能为空");
        }
        SpecialExpenseItem item = super.getById(id);
        if (item == null) {
            return ApiResponse.fail("开支项不存在");
        }
        if (StringUtils.hasText(item.getTodoId())) {
            todoService.delete(item.getTodoId(), actor);
        }
        removeById(id);
        return ApiResponse.success("已删除开支项", null);
    }

    private ApiResponse<Void> validate(SpecialExpenseItem entity) {
        if (!StringUtils.hasText(entity.getProjectId())) {
            return ApiResponse.fail("请选择所属专项");
        }
        if (projectMapper.selectById(entity.getProjectId()) == null) {
            return ApiResponse.fail("专项不存在");
        }
        if (!StringUtils.hasText(entity.getItemName())) {
            return ApiResponse.fail("请输入开支名称");
        }
        return ApiResponse.success(null);
    }

    private void normalize(SpecialExpenseItem entity) {
        if (!StringUtils.hasText(entity.getCategory())) {
            entity.setCategory("OTHER");
        }
        if (!StringUtils.hasText(entity.getStatus())) {
            entity.setStatus("TODO");
        }
        if (entity.getBudgetAmount() == null) {
            entity.setBudgetAmount(BigDecimal.ZERO);
        }
        if (entity.getActualAmount() == null) {
            entity.setActualAmount(BigDecimal.ZERO);
        }
        if (STATUS_DONE.equals(entity.getStatus()) && entity.getPurchaseDate() == null) {
            entity.setPurchaseDate(LocalDate.now());
        }
    }

    private void syncTodo(SpecialExpenseItem item, AppUser actor) {
        if (item.getPlannedTime() == null) {
            return;
        }
        SpecialExpenseProject project = projectMapper.selectById(item.getProjectId());
        Todo todo = StringUtils.hasText(item.getTodoId()) ? todoService.getById(item.getTodoId()) : null;
        if (todo == null) {
            todo = new Todo();
            todo.setRelatedType(RELATED_TYPE);
            todo.setRelatedId(item.getId());
        }
        todo.setTitle("购买：" + item.getItemName());
        todo.setCategory("GROCERY");
        todo.setDescription(todoDescription(project, item));
        todo.setOwnerId(item.getOwnerId());
        todo.setOwnerIds(StringUtils.hasText(item.getOwnerId())
                ? Collections.singletonList(item.getOwnerId())
                : Collections.emptyList());
        todo.setDueTime(item.getPlannedTime());
        todo.setRepeatType("NONE");
        todo.setNotifySite(true);
        todo.setNotifyEmail(false);
        todo.setNotifyPush(false);
        todo.setStatus("TODO");
        todo.setNoticeMinutes(Arrays.asList(1440, 0));
        if (StringUtils.hasText(todo.getId())) {
            todoService.update(todo, actor);
        } else {
            ApiResponse<Todo> created = todoService.create(todo, actor);
            if (created.getData() != null && StringUtils.hasText(created.getData().getId())) {
                item.setTodoId(created.getData().getId());
                super.updateById(item);
            }
        }
    }

    private String todoDescription(SpecialExpenseProject project, SpecialExpenseItem item) {
        StringBuilder text = new StringBuilder();
        if (project != null && StringUtils.hasText(project.getProjectName())) {
            text.append(project.getProjectName()).append('\n');
        }
        if (StringUtils.hasText(item.getQuantity())) {
            text.append("数量：").append(item.getQuantity()).append('\n');
        }
        if (item.getBudgetAmount() != null && item.getBudgetAmount().compareTo(BigDecimal.ZERO) > 0) {
            text.append("预算：").append(item.getBudgetAmount()).append('\n');
        }
        if (StringUtils.hasText(item.getPurchaseLink())) {
            text.append(item.getPurchaseLink()).append('\n');
        }
        if (StringUtils.hasText(item.getRemark())) {
            text.append(item.getRemark());
        }
        return text.toString().trim();
    }

    private void enrich(List<SpecialExpenseItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        Map<String, String> userNames = new HashMap<>();
        List<String> userIds = items.stream()
                .flatMap(item -> Arrays.asList(item.getOwnerId(), item.getPayerId()).stream())
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        if (!userIds.isEmpty()) {
            appUserMapper.selectBatchIds(userIds).forEach(user -> {
                String name = StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername();
                userNames.put(user.getId(), name);
            });
        }
        Map<String, String> projectNames = new HashMap<>();
        List<String> projectIds = items.stream()
                .map(SpecialExpenseItem::getProjectId)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        if (!projectIds.isEmpty()) {
            projectMapper.selectBatchIds(projectIds).forEach(project ->
                    projectNames.put(project.getId(), project.getProjectName()));
        }
        for (SpecialExpenseItem item : items) {
            item.setOwnerName(userNames.get(item.getOwnerId()));
            item.setPayerName(userNames.get(item.getPayerId()));
            item.setProjectName(projectNames.get(item.getProjectId()));
        }
    }

    private boolean isGuest(AppUser actor) {
        return actor != null && ("guest".equals(actor.getId()) || "guest".equals(actor.getUsername()));
    }
}
