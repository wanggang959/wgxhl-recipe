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

    private String seasoningName;

    private String seasoningImage;

    private String seasoningDesc;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
