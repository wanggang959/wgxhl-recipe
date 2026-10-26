package com.wgxhl.recipe.expense.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.config.UserContext;
import com.wgxhl.recipe.expense.dto.SpecialExpenseProjectPageDTO;
import com.wgxhl.recipe.expense.dto.SpecialExpenseSummaryDTO;
import com.wgxhl.recipe.expense.entity.SpecialExpenseItem;
import com.wgxhl.recipe.expense.entity.SpecialExpenseProject;
import com.wgxhl.recipe.expense.mapper.SpecialExpenseItemMapper;
import com.wgxhl.recipe.expense.mapper.SpecialExpenseProjectMapper;
import com.wgxhl.recipe.expense.service.SpecialExpenseProjectService;
import com.wgxhl.recipe.user.entity.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class SpecialExpenseProjectServiceImpl extends ServiceImpl<SpecialExpenseProjectMapper, SpecialExpenseProject>
        implements SpecialExpenseProjectService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_ARCHIVED = "ARCHIVED";
    private static final String ITEM_STATUS_DONE = "DONE";
    private static final String ITEM_STATUS_CANCELLED = "CANCELLED";

    private final SpecialExpenseItemMapper itemMapper;

    public SpecialExpenseProjectServiceImpl(SpecialExpenseItemMapper itemMapper) {
        this.itemMapper = itemMapper;
    }

    @Override
    public boolean save(SpecialExpenseProject entity) {
        if (!StringUtils.hasText(entity.getId())) {
            entity.setId(IdUtil.simpleUUID());
        }
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        if (!StringUtils.hasText(entity.getStatus())) {
            entity.setStatus(STATUS_ACTIVE);
        }
        if (entity.getArchived() == null) {
            entity.setArchived(false);
        }
        if (!StringUtils.hasText(entity.getProjectType())) {
            entity.setProjectType("OTHER");
        }
        if (entity.getTotalBudget() == null) {
            entity.setTotalBudget(BigDecimal.ZERO);
        }
        return super.save(entity);
    }

    @Override
    public boolean updateById(SpecialExpenseProject entity) {
        entity.setUpdateTime(LocalDateTime.now());
        return super.updateById(entity);
    }

    @Override
    public ApiResponse<Page<SpecialExpenseProject>> page(SpecialExpenseProjectPageDTO dto, AppUser actor) {
        Page<SpecialExpenseProject> page = new Page<>(dto.getCurrent(), dto.getSize());
        Page<SpecialExpenseProject> result = lambdaQuery()
                .like(StringUtils.hasText(dto.getKeyword()), SpecialExpenseProject::getProjectName, dto.getKeyword())
                .eq(StringUtils.hasText(dto.getStatus()), SpecialExpenseProject::getStatus, dto.getStatus())
                .eq(dto.getArchived() != null, SpecialExpenseProject::getArchived, dto.getArchived())
                .orderByAsc(SpecialExpenseProject::getArchived)
                .orderByDesc(SpecialExpenseProject::getCreateTime)
                .page(page);
        enrich(result.getRecords());
        return ApiResponse.success(result);
    }

    @Override
    public ApiResponse<SpecialExpenseProject> detail(String id, AppUser actor) {
        if (!StringUtils.hasText(id)) {
            return ApiResponse.fail("专项id不能为空");
        }
        SpecialExpenseProject project = super.getById(id);
        if (project == null) {
            return ApiResponse.fail("专项不存在");
        }
        enrichOne(project);
        return ApiResponse.success(project);
    }

    @Override
    public ApiResponse<SpecialExpenseProject> create(SpecialExpenseProject entity, AppUser actor) {
        if (actor == null) {
            return ApiResponse.fail(401, "请先登录");
        }
        if (isGuest(actor)) {
            return ApiResponse.fail("游客不能创建专项");
        }
        if (!StringUtils.hasText(entity.getProjectName())) {
            return ApiResponse.fail("专项名称不能为空");
        }
        entity.setCreateUserId(actor.getId());
        save(entity);
        enrichOne(entity);
        return ApiResponse.success("已创建专项", entity);
    }

    @Override
    public ApiResponse<Void> update(SpecialExpenseProject entity, AppUser actor) {
        if (isGuest(actor)) {
            return ApiResponse.fail("游客不能编辑专项");
        }
        if (!StringUtils.hasText(entity.getId())) {
            return ApiResponse.fail("专项id不能为空");
        }
        SpecialExpenseProject existing = super.getById(entity.getId());
        if (existing == null) {
            return ApiResponse.fail("专项不存在");
        }
        String editDenied = manageDeniedReason(existing, actor);
        if (editDenied != null) {
            return ApiResponse.fail(403, editDenied);
        }
        if (!StringUtils.hasText(entity.getProjectName())) {
            return ApiResponse.fail("专项名称不能为空");
        }
        if (entity.getTotalBudget() == null) {
            entity.setTotalBudget(BigDecimal.ZERO);
        }
        entity.setCreateUserId(existing.getCreateUserId());
        updateById(entity);
        return ApiResponse.success("已更新专项", null);
    }

    @Override
    public ApiResponse<Void> archive(String id, AppUser actor) {
        return setArchived(id, true, actor);
    }

    @Override
    public ApiResponse<Void> restore(String id, AppUser actor) {
        return setArchived(id, false, actor);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Void> delete(String id, AppUser actor) {
        if (isGuest(actor)) {
            return ApiResponse.fail("游客不能删除专项");
        }
        if (!StringUtils.hasText(id)) {
            return ApiResponse.fail("专项id不能为空");
        }
        SpecialExpenseProject project = super.getById(id);
        if (project == null) {
            return ApiResponse.fail("专项不存在");
        }
        String deleteDenied = manageDeniedReason(project, actor);
        if (deleteDenied != null) {
            return ApiResponse.fail(403, deleteDenied);
        }
        Long count = itemMapper.selectCount(new LambdaQueryWrapper<SpecialExpenseItem>()
                .eq(SpecialExpenseItem::getProjectId, id));
        if (count != null && count > 0) {
            return ApiResponse.fail("专项下已有开支记录，请先清理或归档");
        }
        removeById(id);
        return ApiResponse.success("已删除专项", null);
    }

    @Override
    public ApiResponse<SpecialExpenseSummaryDTO> summary(String projectId, String category, String status, AppUser actor) {
        if (!StringUtils.hasText(projectId)) {
            return ApiResponse.fail("专项id不能为空");
        }
        SpecialExpenseProject project = super.getById(projectId);
        if (project == null) {
            return ApiResponse.fail("专项不存在");
        }
        return ApiResponse.success(buildSummary(projectId, project.getTotalBudget(), category, status));
    }

    private ApiResponse<Void> setArchived(String id, boolean archived, AppUser actor) {
        if (isGuest(actor)) {
            return ApiResponse.fail("游客不能操作专项");
        }
        if (!StringUtils.hasText(id)) {
            return ApiResponse.fail("专项id不能为空");
        }
        SpecialExpenseProject project = super.getById(id);
        if (project == null) {
            return ApiResponse.fail("专项不存在");
        }
        String archiveDenied = manageDeniedReason(project, actor);
        if (archiveDenied != null) {
            return ApiResponse.fail(403, archiveDenied);
        }
        project.setArchived(archived);
        project.setStatus(archived ? STATUS_ARCHIVED : STATUS_ACTIVE);
        updateById(project);
        return ApiResponse.success(archived ? "已归档专项" : "已恢复专项", null);
    }

    private void enrich(List<SpecialExpenseProject> projects) {
        if (projects == null || projects.isEmpty()) {
            return;
        }
        for (SpecialExpenseProject project : projects) {
            enrichOne(project);
        }
    }

    private void enrichOne(SpecialExpenseProject project) {
        if (project == null || !StringUtils.hasText(project.getId())) {
            return;
        }
        SpecialExpenseSummaryDTO summary = buildSummary(project.getId(), project.getTotalBudget(), null, null);
        project.setActualAmount(summary.getActualAmount());
        project.setRemainingBudget(summary.getRemainingBudget());
        project.setItemCount(summary.getItemCount());
        project.setPendingCount(summary.getPendingCount());
        project.setDoneCount(summary.getDoneCount());
    }

    private SpecialExpenseSummaryDTO buildSummary(String projectId, BigDecimal totalBudget, String category, String status) {
        SpecialExpenseSummaryDTO summary = new SpecialExpenseSummaryDTO();
        List<SpecialExpenseItem> items = itemMapper.selectList(new LambdaQueryWrapper<SpecialExpenseItem>()
                .eq(SpecialExpenseItem::getProjectId, projectId)
                .eq(StringUtils.hasText(category), SpecialExpenseItem::getCategory, category)
                .eq(StringUtils.hasText(status), SpecialExpenseItem::getStatus, status));
        BigDecimal actual = BigDecimal.ZERO;
        BigDecimal itemBudgetTotal = BigDecimal.ZERO;
        long pending = 0L;
        long done = 0L;
        long overBudget = 0L;
        for (SpecialExpenseItem item : items) {
            BigDecimal itemActual = item.getActualAmount() == null ? BigDecimal.ZERO : item.getActualAmount();
            BigDecimal itemBudget = item.getBudgetAmount() == null ? BigDecimal.ZERO : item.getBudgetAmount();
            actual = actual.add(itemActual);
            itemBudgetTotal = itemBudgetTotal.add(itemBudget);
            if (ITEM_STATUS_DONE.equals(item.getStatus())) {
                done++;
            } else if (!ITEM_STATUS_CANCELLED.equals(item.getStatus())) {
                pending++;
            }
            if (itemBudget.compareTo(BigDecimal.ZERO) > 0 && itemActual.compareTo(itemBudget) > 0) {
                overBudget++;
            }
        }
        boolean filtered = StringUtils.hasText(category) || StringUtils.hasText(status);
        BigDecimal scopeBudget = filtered
                ? itemBudgetTotal
                : (totalBudget == null ? BigDecimal.ZERO : totalBudget);
        summary.setTotalBudget(scopeBudget);
        summary.setActualAmount(actual);
        summary.setRemainingBudget(scopeBudget.subtract(actual));
        summary.setItemCount((long) items.size());
        summary.setPendingCount(pending);
        summary.setDoneCount(done);
        summary.setOverBudgetCount(overBudget);
        return summary;
    }

    private boolean isGuest(AppUser actor) {
        return actor != null && ("guest".equals(actor.getId()) || "guest".equals(actor.getUsername()));
    }

    /**
     * 专项归家庭所有：创建人可以操作，家庭管理员（owner/admin）可代管。
     *
     * @return 允许操作返回 null，否则返回提示语
     */
    private String manageDeniedReason(SpecialExpenseProject project, AppUser actor) {
        if (UserContext.isHouseholdManager()) {
            return null;
        }
        if (actor != null && StringUtils.hasText(actor.getId())
                && actor.getId().equals(project.getCreateUserId())) {
            return null;
        }
        return "只有专项创建人或家庭管理员可以操作";
    }
}
