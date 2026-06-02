package com.wgxhl.recipe.step.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("recipe_step")
public class RecipeStep {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /*
     * 菜谱id
     */
    private String recipeId;

    /*
     * 步骤序号
     */
    private Integer stepNo;

    /*
     * 步骤标题
     */
    private String stepTitle;

    /*
     * 步骤说明
     */
    private String stepDesc;

    /*
     * 步骤图片地址
     */
    private String stepImage;

    /*
     * 创建时间
     */
    private LocalDateTime createTime;

    /*
     * 更新时间
     */
    private LocalDateTime updateTime;
}
