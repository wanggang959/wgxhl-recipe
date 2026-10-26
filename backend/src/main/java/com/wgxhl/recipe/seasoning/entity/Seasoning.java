package com.wgxhl.recipe.seasoning.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("seasoning")
public class Seasoning {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /*
     * 调料名称
     */
    private String seasoningName;

    /*
     * 调料图片地址
     */
    private String seasoningImage;

    /*
     * 调料描述
     */
    private String seasoningDesc;

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
