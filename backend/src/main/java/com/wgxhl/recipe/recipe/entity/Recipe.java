package com.wgxhl.recipe.recipe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("recipe")
public class Recipe {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /*
     * 菜谱名称
     */
    private String recipeName;

    /*
     * 版本号（如 1.0）
     */
    private String recipeVersion;

    /*
     * 菜谱简介
     */
    private String recipeDesc;

    /*
     * 封面图片地址
     */
    private String coverImage;

    /*
     * 难度
     */
    private String difficulty;

    /*
     * 制作耗时
     */
    private String cookingTime;

    /*
     * 适合人数
     */
    private String servingCount;

    /*
     * 口味
     */
    private String taste;

    /*
     * 状态
     */
    private String status;

    /*
     * 分类id
     */
    private String categoryId;

    /*
     * 分类名称冗余
     */
    private String categoryName;

    /*
     * 上传者用户id
     */
    private String ownerUserId;

    /*
     * 上传者显示名（昵称优先）
     */
    private String ownerName;

    /*
     * 备注
     */
    private String remark;

    /*
     * 创建时间
     */
    private LocalDateTime createTime;

    /*
     * 更新时间
     */
    private LocalDateTime updateTime;
}
