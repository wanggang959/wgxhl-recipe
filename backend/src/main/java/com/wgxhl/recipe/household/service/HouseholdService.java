package com.wgxhl.recipe.household.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wgxhl.recipe.household.entity.Household;

import java.util.List;

public interface HouseholdService extends IService<Household> {

    List<Household> listActive();
}
