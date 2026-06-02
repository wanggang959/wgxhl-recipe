package com.wgxhl.recipe.relation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("recipe_ingredient_rel")
public class RecipeIngredientRel {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /*
     * 菜谱id
     */
    private String recipeId;

    /*
     * 食材id
     */
    private String ingredientId;

    /*
     * 食材名称冗余
     */
    private String ingredientName;

    /*
     * 用量
     */
    private String amount;

    /*
     * 单位
     */
    private String unit;

    /*
     * 排序号
     */
    private Integer sortNo;

    /*
     * 创建时间
     */
    private LocalDateTime createTime;

    /*
     * 更新时间
     */
    private LocalDateTime updateTime;
}
