package com.wgxhl.recipe.household.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class HouseholdInvitePreviewVO {

    private String householdName;

    private String memberRole;

    private String inviterName;

    private LocalDateTime expiresAt;

    private Integer maxUses;

    private Integer usedCount;
}
