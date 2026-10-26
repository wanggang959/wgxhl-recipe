package com.wgxhl.recipe.category.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("recipe_category")
public class RecipeCategory {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /*
     * 分类名称
     */
    private String categoryName;

    /*
     * 分类编码
     */
    private String categoryCode;

    /*
     * 排序号
     */
    private Integer sortNo;

    /*
     * 分类描述
     */
    private String categoryDesc;

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
