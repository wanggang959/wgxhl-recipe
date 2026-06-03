package com.wgxhl.recipe.want.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("user_wanted_recipe")
public class UserWantedRecipe {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /*
     * 用户id
     */
    private String userId;

    /*
     * 菜谱id
     */
    private String recipeId;

    /*
     * 菜谱名称冗余
     */
    private String recipeName;

    /*
     * 封面图片地址冗余
     */
    private String coverImage;

    /*
     * 想吃日期
     */
    private LocalDate plannedDate;

    /*
     * 创建时间
     */
    private LocalDateTime createTime;

    /*
     * 更新时间
     */
    private LocalDateTime updateTime;
}
