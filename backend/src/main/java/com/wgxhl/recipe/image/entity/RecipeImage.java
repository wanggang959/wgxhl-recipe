package com.wgxhl.recipe.image.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("recipe_image")
public class RecipeImage {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /*
     * 菜谱id
     */
    private String recipeId;

    /*
     * 图片类型：cover-封面，ingredient-食材，step-步骤，finish-成品
     */
    private String imageType;

    /*
     * 图片地址
     */
    private String imageUrl;

    /*
     * 排序号
     */
    private Integer sortNo;

    /*
     * 图片描述
     */
    private String imageDesc;

    /*
     * 创建时间
     */
    private LocalDateTime createTime;

    /*
     * 更新时间
     */
    private LocalDateTime updateTime;
}
