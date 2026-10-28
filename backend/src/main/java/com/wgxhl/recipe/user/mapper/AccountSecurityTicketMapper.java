package com.wgxhl.recipe.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wgxhl.recipe.user.entity.AccountSecurityTicket;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AccountSecurityTicketMapper extends BaseMapper<AccountSecurityTicket> {
    @Select("select * from account_security_ticket where token_hash=#{hash} and purpose='RESET_PASSWORD' for update")
    AccountSecurityTicket lockByToken(@Param("hash") String hash);

    @Select("select * from account_security_ticket where id=#{id} for update")
    AccountSecurityTicket lockById(@Param("id") String id);
}
