package com.wgxhl.recipe.household.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wgxhl.recipe.household.entity.HouseholdMember;

import java.util.List;

public interface HouseholdMemberService extends IService<HouseholdMember> {

    List<HouseholdMember> listByHousehold(String householdId);

    List<HouseholdMember> listByUser(String userId);

    HouseholdMember find(String householdId, String userId);

    HouseholdMember findFirstByUser(String userId);

    List<String> listUserIds(String householdId);
}
