package com.wgxhl.recipe.household.vo;

import lombok.Data;

@Data
public class HouseholdVO {

    private String id;

    private String householdName;

    private String ownerUserId;

    private String memberRole;

    private Long memberCount;
}
