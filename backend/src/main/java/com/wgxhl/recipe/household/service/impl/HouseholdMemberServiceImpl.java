package com.wgxhl.recipe.household.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wgxhl.recipe.household.entity.HouseholdMember;
import com.wgxhl.recipe.household.mapper.HouseholdMemberMapper;
import com.wgxhl.recipe.household.service.HouseholdMemberService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class HouseholdMemberServiceImpl extends ServiceImpl<HouseholdMemberMapper, HouseholdMember>
        implements HouseholdMemberService {

    @Override
    public boolean save(HouseholdMember entity) {
        if (!StringUtils.hasText(entity.getId())) {
            entity.setId(IdUtil.simpleUUID());
        }
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        if (!StringUtils.hasText(entity.getMemberRole())) {
            entity.setMemberRole("member");
        }
        if (!StringUtils.hasText(entity.getStatus())) {
            entity.setStatus("normal");
        }
        return super.save(entity);
    }

    @Override
    public boolean updateById(HouseholdMember entity) {
        entity.setUpdateTime(LocalDateTime.now());
        return super.updateById(entity);
    }

    @Override
    public List<HouseholdMember> listByHousehold(String householdId) {
        if (!StringUtils.hasText(householdId)) {
            return Collections.emptyList();
        }
        return lambdaQuery()
                .eq(HouseholdMember::getHouseholdId, householdId)
                .orderByAsc(HouseholdMember::getCreateTime)
                .list();
    }

    @Override
    public List<HouseholdMember> listByUser(String userId) {
        if (!StringUtils.hasText(userId)) {
            return Collections.emptyList();
        }
        return lambdaQuery()
                .eq(HouseholdMember::getUserId, userId)
                .eq(HouseholdMember::getStatus, "normal")
                .orderByAsc(HouseholdMember::getCreateTime)
                .list();
    }

    @Override
    public HouseholdMember find(String householdId, String userId) {
        if (!StringUtils.hasText(householdId) || !StringUtils.hasText(userId)) {
            return null;
        }
        return lambdaQuery()
                .eq(HouseholdMember::getHouseholdId, householdId)
                .eq(HouseholdMember::getUserId, userId)
                .one();
    }

    @Override
    public HouseholdMember findFirstByUser(String userId) {
        if (!StringUtils.hasText(userId)) {
            return null;
        }
        return lambdaQuery()
                .eq(HouseholdMember::getUserId, userId)
                .eq(HouseholdMember::getStatus, "normal")
                .orderByAsc(HouseholdMember::getCreateTime)
                .last("limit 1")
                .one();
    }

    @Override
    public List<String> listUserIds(String householdId) {
        return listByHousehold(householdId).stream()
                .filter(member -> "normal".equals(member.getStatus()))
                .map(HouseholdMember::getUserId)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
    }
}
