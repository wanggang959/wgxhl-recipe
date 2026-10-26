package com.wgxhl.recipe.household.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wgxhl.recipe.household.entity.HouseholdInvite;

import java.util.List;

public interface HouseholdInviteService extends IService<HouseholdInvite> {

    HouseholdInvite createInvite(String householdId, String memberRole, Integer maxUses,
                                 Integer expiresInHours, String createdBy);

    HouseholdInvite findUsable(String inviteCode);

    List<HouseholdInvite> listActive(String householdId);

    boolean revoke(String id, String householdId);

    boolean consume(HouseholdInvite invite);
}
