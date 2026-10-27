package com.wgxhl.recipe.household.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wgxhl.recipe.household.entity.HouseholdInvite;
import com.wgxhl.recipe.household.mapper.HouseholdInviteMapper;
import com.wgxhl.recipe.household.service.HouseholdInviteService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class HouseholdInviteServiceImpl extends ServiceImpl<HouseholdInviteMapper, HouseholdInvite>
        implements HouseholdInviteService {

    private static final String STATUS_ACTIVE = "active";
    private static final String STATUS_REVOKED = "revoked";
    private static final String STATUS_EXHAUSTED = "exhausted";
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    @Override
    public boolean save(HouseholdInvite entity) {
        if (!StringUtils.hasText(entity.getId())) {
            entity.setId(IdUtil.simpleUUID());
        }
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        if (!StringUtils.hasText(entity.getStatus())) {
            entity.setStatus(STATUS_ACTIVE);
        }
        if (entity.getUsedCount() == null) {
            entity.setUsedCount(0);
        }
        return super.save(entity);
    }

    @Override
    public boolean updateById(HouseholdInvite entity) {
        entity.setUpdateTime(LocalDateTime.now());
        return super.updateById(entity);
    }

    @Override
    public HouseholdInvite createInvite(String householdId, String memberRole, Integer maxUses,
                                        Integer expiresInHours, String createdBy) {
        String role = "admin".equals(memberRole) ? "admin" : "member";
        int uses = maxUses == null ? 1 : Math.max(1, Math.min(maxUses, 50));
        int hours = expiresInHours == null ? 72 : Math.max(1, Math.min(expiresInHours, 720));
        String code;
        do {
            code = RandomUtil.randomString(CODE_CHARS, 8);
        } while (lambdaQuery().eq(HouseholdInvite::getInviteCode, code).exists());

        HouseholdInvite invite = new HouseholdInvite();
        invite.setHouseholdId(householdId);
        invite.setInviteCode(code);
        invite.setMemberRole(role);
        invite.setMaxUses(uses);
        invite.setUsedCount(0);
        invite.setExpiresAt(LocalDateTime.now().plusHours(hours));
        invite.setStatus(STATUS_ACTIVE);
        invite.setCreatedBy(createdBy);
        save(invite);
        return invite;
    }

    @Override
    public HouseholdInvite findUsable(String inviteCode) {
        if (!StringUtils.hasText(inviteCode)) {
            return null;
        }
        String code = inviteCode.trim().toUpperCase();
        HouseholdInvite invite = lambdaQuery()
                .eq(HouseholdInvite::getInviteCode, code)
                .eq(HouseholdInvite::getStatus, STATUS_ACTIVE)
                .one();
        if (invite == null) {
            return null;
        }
        if (invite.getExpiresAt() != null && invite.getExpiresAt().isBefore(LocalDateTime.now())) {
            return null;
        }
        int maxUses = invite.getMaxUses() == null ? 1 : invite.getMaxUses();
        int usedCount = invite.getUsedCount() == null ? 0 : invite.getUsedCount();
        return usedCount < maxUses ? invite : null;
    }

    @Override
    public List<HouseholdInvite> listActive(String householdId) {
        LocalDateTime now = LocalDateTime.now();
        return lambdaQuery()
                .eq(HouseholdInvite::getHouseholdId, householdId)
                .eq(HouseholdInvite::getStatus, STATUS_ACTIVE)
                .orderByDesc(HouseholdInvite::getCreateTime)
                .list()
                .stream()
                .filter(invite -> invite.getExpiresAt() == null || invite.getExpiresAt().isAfter(now))
                .filter(invite -> (invite.getUsedCount() == null ? 0 : invite.getUsedCount())
                        < (invite.getMaxUses() == null ? 1 : invite.getMaxUses()))
                .collect(Collectors.toList());
    }

    @Override
    public boolean revoke(String id, String householdId) {
        if (!StringUtils.hasText(id) || !StringUtils.hasText(householdId)) {
            return false;
        }
        return lambdaUpdate()
                .eq(HouseholdInvite::getId, id)
                .eq(HouseholdInvite::getHouseholdId, householdId)
                .eq(HouseholdInvite::getStatus, STATUS_ACTIVE)
                .set(HouseholdInvite::getStatus, STATUS_REVOKED)
                .set(HouseholdInvite::getUpdateTime, LocalDateTime.now())
                .update();
    }

    @Override
    public boolean consume(HouseholdInvite invite) {
        if (invite == null || !StringUtils.hasText(invite.getId())) {
            return false;
        }
        int maxUses = invite.getMaxUses() == null ? 1 : invite.getMaxUses();
        boolean updated = lambdaUpdate()
                .eq(HouseholdInvite::getId, invite.getId())
                .eq(HouseholdInvite::getStatus, STATUS_ACTIVE)
                .lt(HouseholdInvite::getUsedCount, maxUses)
                .setSql("used_count = used_count + 1")
                .set(HouseholdInvite::getUpdateTime, LocalDateTime.now())
                .update();
        if (!updated) {
            return false;
        }
        HouseholdInvite latest = getById(invite.getId());
        if (latest != null && latest.getUsedCount() != null
                && latest.getUsedCount() >= (latest.getMaxUses() == null ? 1 : latest.getMaxUses())) {
            HouseholdInvite patch = new HouseholdInvite();
            patch.setId(latest.getId());
            patch.setStatus(STATUS_EXHAUSTED);
            updateById(patch);
        }
        return true;
    }
}
