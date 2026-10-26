package com.wgxhl.recipe.household.dto;

import lombok.Data;

@Data
public class HouseholdInviteCreateDTO {

    private String memberRole;

    private Integer maxUses;

    private Integer expiresInHours;
}
