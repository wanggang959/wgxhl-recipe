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

    /*
     * 菜谱id
     */
    private String recipeId;

    /*
     * 调料id
     */
    private String seasoningId;

    /*
     * 调料名称冗余
     */
    private String seasoningName;

    /*
     * 调料图片地址（关联查询，非表字段）
     */
    @TableField(exist = false)
    private String seasoningImage;

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
