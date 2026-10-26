package com.wgxhl.recipe.household.service;

import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.household.entity.Household;
import com.wgxhl.recipe.household.entity.HouseholdMember;
import com.wgxhl.recipe.user.entity.AppUser;

public interface HouseholdLeaveService {

    ApiResponse<AppUser> leave(AppUser user, Household household, HouseholdMember membership);

    ApiResponse<AppUser> transfer(AppUser user, Household household, HouseholdMember membership, String targetUserId);

    ApiResponse<AppUser> dissolve(AppUser user, Household household, HouseholdMember membership);
}
