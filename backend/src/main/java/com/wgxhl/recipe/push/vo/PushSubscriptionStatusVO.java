package com.wgxhl.recipe.push.vo;

import lombok.Data;

@Data
public class PushSubscriptionStatusVO {

    private String publicKey;

    private Boolean configured;

    private Boolean subscribed;
}
