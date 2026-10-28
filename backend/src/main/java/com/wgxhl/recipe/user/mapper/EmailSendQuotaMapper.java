package com.wgxhl.recipe.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wgxhl.recipe.user.entity.EmailSendQuota;
import org.apache.ibatis.annotations.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Mapper
public interface EmailSendQuotaMapper extends BaseMapper<EmailSendQuota> {
    @Insert("insert into email_send_quota (id,bucket_type,bucket_hash,quota_date,used_count,create_time,update_time) " +
            "values (#{id},#{type},#{hash},#{day},0,#{now},#{now}) on duplicate key update id=id")
    int ensure(@Param("id") String id, @Param("type") String type, @Param("hash") String hash,
               @Param("day") LocalDate day, @Param("now") LocalDateTime now);

    @Select("select * from email_send_quota where bucket_type=#{type} and bucket_hash=#{hash} and quota_date=#{day} for update")
    EmailSendQuota lock(@Param("type") String type, @Param("hash") String hash, @Param("day") LocalDate day);

    @Select("select max(last_sent_at) from email_send_quota where bucket_type='EMAIL' and bucket_hash=#{hash} and quota_date>=#{day}")
    LocalDateTime lastEmailSent(@Param("hash") String hash, @Param("day") LocalDate day);
}
