package com.wgxhl.recipe.favorite.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_favorite")
public class UserFavorite {

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
     * 创建时间
     */
    private LocalDateTime createTime;

    /*
     * 更新时间
     */
    private LocalDateTime updateTime;
}
