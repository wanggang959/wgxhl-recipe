package com.wgxhl.recipe.user.wechat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgxhl.recipe.config.JwtAuthUtil;
import com.wgxhl.recipe.user.mapper.AppUserMapper;
import com.wgxhl.recipe.user.util.PasswordUtil;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
@EnabledIfSystemProperty(named = "test.integration.enabled", matches = "true")
@Transactional
class WechatBindingIntegrationTest {
    @Autowired JdbcTemplate db;
    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    @Autowired JwtAuthUtil jwt;
    @Autowired AppUserMapper users;
    @MockBean WechatIdentityClient wechat;

    @Test void identityAndUserAreUniqueAndDeletionCascades() {
        db.update("insert into app_user (id, username, status) values (?, ?, ?)", "wx-test-one", "wx-test-one", "normal");
        db.update("insert into app_user (id, username, status) values (?, ?, ?)", "wx-test-two", "wx-test-two", "normal");
        db.update("insert into user_wechat_binding (user_id, app_id, open_id) values (?, ?, ?)", "wx-test-one", "app", "openid");
        assertThrows(DuplicateKeyException.class, () -> db.update("insert into user_wechat_binding (user_id, app_id, open_id) values (?, ?, ?)", "wx-test-two", "app", "openid"));
        assertThrows(DuplicateKeyException.class, () -> db.update("insert into user_wechat_binding (user_id, app_id, open_id) values (?, ?, ?)", "wx-test-one", "app", "other"));
        db.update("delete from app_user where id=?", "wx-test-one");
        assertEquals(0, db.queryForObject("select count(*) from user_wechat_binding where user_id=?", Integer.class, "wx-test-one"));
    }

    @Test void onlyOwnerWithPasswordCanSeeAndUnbindIdentity() throws Exception {
        String home="00000000-0000-0000-0000-000000000001", id=UUID.randomUUID().toString();
        db.update("insert into app_user (id,username,password,status,user_role,auth_version) values (?,?,?,'normal','user',0)", id,id,PasswordUtil.hash("correct-password"));
        db.update("insert into household_member (id,household_id,user_id,member_role,status) values (?,?,?,'member','normal')",UUID.randomUUID().toString(),home,id);
        db.update("insert into user_wechat_binding (user_id,app_id,open_id) values (?,?,?)",id,"test-app","test-openid");
        when(wechat.appId()).thenReturn("test-app");
        String token=jwt.createToken(users.selectById(id),home);
        assertEquals(401, result("/user/wechat/status",null,"{}").get("status").asInt());
        assertTrue(result("/user/wechat/status",token,"{}").get("data").get("bound").asBoolean());
        assertNotEquals(200,result("/user/wechat/unbind",token,"{\"password\":\"wrong-password\"}").get("status").asInt());
        assertEquals(1,db.queryForObject("select count(*) from user_wechat_binding where user_id=?",Integer.class,id));
        assertEquals(200,result("/user/wechat/unbind",token,"{\"password\":\"correct-password\"}").get("status").asInt());
        assertFalse(result("/user/wechat/status",token,"{}").get("data").get("bound").asBoolean());
    }

    private com.fasterxml.jackson.databind.JsonNode result(String path,String token,String body) throws Exception {
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request=post("/recipe"+path).contextPath("/recipe").contentType("application/json").content(body);
        if(token!=null)request.header("Authorization","Bearer "+token);
        MvcResult response=http.perform(request).andReturn();
        return json.readTree(response.getResponse().getContentAsString());
    }
}
