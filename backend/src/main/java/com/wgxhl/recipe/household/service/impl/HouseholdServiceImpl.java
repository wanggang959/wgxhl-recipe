package com.wgxhl.recipe.household.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wgxhl.recipe.household.entity.Household;
import com.wgxhl.recipe.household.mapper.HouseholdMapper;
import com.wgxhl.recipe.household.service.HouseholdService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class HouseholdServiceImpl extends ServiceImpl<HouseholdMapper, Household>
        implements HouseholdService {

    @Override
    public boolean save(Household entity) {
        if (!StringUtils.hasText(entity.getId())) {
            entity.setId(IdUtil.simpleUUID());
        }
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        if (!StringUtils.hasText(entity.getStatus())) {
            entity.setStatus("normal");
        }
        return super.save(entity);
    }

    @Override
    public boolean updateById(Household entity) {
        entity.setUpdateTime(LocalDateTime.now());
        return super.updateById(entity);
    }

    @Override
    public List<Household> listActive() {
        return lambdaQuery()
                .eq(Household::getStatus, "normal")
                .orderByAsc(Household::getCreateTime)
                .list();
    }
}
