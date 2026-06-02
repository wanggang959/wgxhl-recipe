package com.wgxhl.recipe.record.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("recipe_view_record")
public class RecipeViewRecord {

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
     * 浏览时间
     */
    private LocalDateTime viewTime;

    /*
     * 创建时间
     */
    private LocalDateTime createTime;

    /*
     * 更新时间
     */
    private LocalDateTime updateTime;
}
