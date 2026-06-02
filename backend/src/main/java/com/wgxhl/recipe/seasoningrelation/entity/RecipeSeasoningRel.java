package com.wgxhl.recipe.seasoningrelation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("recipe_seasoning_rel")
public class RecipeSeasoningRel {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String recipeId;

    private String seasoningId;

    private String seasoningName;

    @TableField(exist = false)
    private String seasoningImage;

    private String amount;

    private String unit;

    private Integer sortNo;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
