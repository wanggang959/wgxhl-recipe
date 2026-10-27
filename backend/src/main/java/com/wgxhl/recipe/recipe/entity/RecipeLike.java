package com.wgxhl.recipe.recipe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("recipe_like")
public class RecipeLike {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String recipeId;

    private String userId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
