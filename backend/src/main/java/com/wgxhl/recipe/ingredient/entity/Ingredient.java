package com.wgxhl.recipe.ingredient.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ingredient")
public class Ingredient {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /*
     * 食材名称
     */
    private String ingredientName;

    /*
     * 食材图片地址
     */
    private String ingredientImage;

    /*
     * 食材描述
     */
    private String ingredientDesc;

    /*
     * 数据范围：platform-平台公共，personal-个人
     */
    private String scope;

    /*
     * 归属人：平台数据固定为 platform，个人数据为用户id
     */
    private String ownerUserId;

    /*
     * 创建时间
     */
    private LocalDateTime createTime;

    /*
     * 更新时间
     */
    private LocalDateTime updateTime;
}
