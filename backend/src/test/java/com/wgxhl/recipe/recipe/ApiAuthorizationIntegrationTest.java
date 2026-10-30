package com.wgxhl.recipe.recipe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgxhl.recipe.config.JwtAuthUtil;
import com.wgxhl.recipe.user.mapper.AppUserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
@EnabledIfSystemProperty(named="test.integration.enabled", matches="true")
@Transactional
class ApiAuthorizationIntegrationTest {
    private static final String HOME="00000000-0000-0000-0000-000000000001";
    private static final String OWNER="admin-wangshifu";
    private static final String[] CHILDREN={"recipeStep", "recipeImage", "recipeIngredient", "recipeSeasoningRel"};
    private static final String[] TABLES={"recipe_step", "recipe_image", "recipe_ingredient_rel", "recipe_seasoning_rel"};
    @Autowired private JdbcTemplate db;
    @Autowired private MockMvc http;
    @Autowired private JwtAuthUtil jwt;
    @Autowired private AppUserMapper users;
    @Autowired private ObjectMapper json;
    @Autowired private org.mybatis.spring.SqlSessionTemplate sessions;

    private JsonNode call(String path, Object body, String user) throws Exception {
        // JDBC fixture updates must be visible as they would be across real HTTP requests.
        sessions.clearCache();
        String token=jwt.createToken(users.selectById(user), HOME);
        String response=http.perform(post("/recipe"+path).contextPath("/recipe")
                .header("Authorization", "Bearer "+token).contentType("application/json")
                .content(json.writeValueAsBytes(body))).andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    @Test
    void guestCannotReadAccountDetailsAndReceivesOnlyBasicMemberProfiles() throws Exception {
        String member=account(true);
        assertEquals(403, call("/user/getById", Map.of("id", member), "guest").get("status").asInt());
        JsonNode profiles=call("/user/page", Map.of("current",1,"size",50,"username",member), "guest");
        assertEquals(200, profiles.get("status").asInt());
        JsonNode profile=profiles.get("data").get("records").get(0);
        assertEquals(member,profile.get("id").asText());
        assertNoValue(profile,"email","birthday","password","remark","authVersion","accountOrigin");
        JsonNode members=call("/user/members",Map.of(),"guest").get("data");
        for (JsonNode item : members) assertNoValue(item,"email","birthday","notificationPreference");
    }

    @Test
    void accountDetailsRequireSelfOrCurrentFamilyManager() throws Exception {
        String member=account(true), outsider=account(false);
        assertEquals(403,call("/user/getById",Map.of("id",outsider),OWNER).get("status").asInt());
        assertEquals(403,call("/user/getById",Map.of("id",OWNER),member).get("status").asInt());
        JsonNode self=call("/user/getById",Map.of("id",member),member);
        assertEquals(200,self.get("status").asInt());
        assertEquals(member+"@example.test",self.get("data").get("email").asText());
        JsonNode managed=call("/user/getById",Map.of("id",member),OWNER);
        assertEquals(200,managed.get("status").asInt());
        assertNoValue(managed.get("data"),"password","authVersion","accountOrigin","createdByUserId","createdHouseholdId","activeHouseholdId");
    }

    @Test
    void allChildReadsFollowPrivatePublicFamilyAndOfflineVisibility() throws Exception {
        String other=account(false), member=account(true);
        String recipe=recipe(other);
        children(recipe);
        assertChildStatus(recipe,"guest",403);
        assertChildStatus(recipe,OWNER,403);
        assertNotEquals(200,call("/recipe/getById",Map.of("id",recipe),OWNER).get("status").asInt());
        db.update("update recipe set visibility='public' where id=?",recipe);
        assertChildStatus(recipe,"guest",200);
        db.update("update recipe set status='下架' where id=?",recipe);
        assertChildStatus(recipe,"guest",403);
        db.update("update recipe set status='上架', visibility='family' where id=?",recipe);
        assertChildStatus(recipe,OWNER,403);
        db.update("update recipe set owner_user_id=? where id=?",member,recipe);
        assertChildStatus(recipe,OWNER,200);
        db.update("update recipe set visibility='private',status='下架' where id=?",recipe);
        assertChildStatus(recipe,member,200);
    }

    @Test
    void allChildrenRejectReparentingAndPreserveNormalAuthorEdits() throws Exception {
        String own=recipe(OWNER), other=recipe(account(false));
        String[] ids=children(own);
        for (int i=0;i<CHILDREN.length;i++) {
            JsonNode denied=call("/"+CHILDREN[i]+"/update",Map.of("id",ids[i],"recipeId",other),OWNER);
            assertEquals(403,denied.get("status").asInt(),denied.toString());
            assertEquals(own,db.queryForObject("select recipe_id from "+TABLES[i]+" where id=?",String.class,ids[i]));
            assertEquals(200,call("/"+CHILDREN[i]+"/update",Map.of("id",ids[i],"recipeId",own),OWNER).get("status").asInt());
            assertEquals(200,call("/"+CHILDREN[i]+"/update",Map.of("id",ids[i],"recipeId",""),OWNER).get("status").asInt());
            assertEquals(own,db.queryForObject("select recipe_id from "+TABLES[i]+" where id=?",String.class,ids[i]));
        }
    }

    @Test
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void failedUpdateRollsBackTitleAndAllPreviouslySavedChildren() throws Exception {
        String recipe=recipe(OWNER);
        String[] ids=children(recipe);
        try {
            for (String invalidPart : new String[]{"ingredientList","imageList","seasoningList"}) {
                Map<String,Object> body=validBody(recipe);
                if ("imageList".equals(invalidPart)) body.put(invalidPart,List.of(Map.of("imageType","cover")));
                else body.put(invalidPart,List.of(Map.of("ingredientId","missing-review-ingredient")));
                JsonNode response=call("/recipe/update",body,OWNER);
                assertNotEquals(200,response.get("status").asInt(),response.toString());
                assertEquals(recipe,db.queryForObject("select recipe_name from recipe where id=?",String.class,recipe));
                for (int i=0;i<TABLES.length;i++) assertEquals(1,db.queryForObject("select count(*) from "+TABLES[i]+" where id=?",Integer.class,ids[i]));
            }
        } finally { removeRecipe(recipe); }
    }

    @Test
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void failedCreateLeavesNoRecipeOrChildrenAndValidSaveStillWorks() throws Exception {
        String id=UUID.randomUUID().toString();
        try {
            Map<String,Object> body=validBody(id);
            body.put("ingredientList",List.of(Map.of("ingredientId","missing-review-ingredient")));
            JsonNode response=call("/recipe/create",body,OWNER);
            assertNotEquals(200,response.get("status").asInt(),response.toString());
            assertEquals(0,db.queryForObject("select count(*) from recipe where id=?",Integer.class,id));
            for (String table : TABLES) assertEquals(0,db.queryForObject("select count(*) from "+table+" where recipe_id=?",Integer.class,id));
            response=call("/recipe/create",validBody(id),OWNER);
            assertEquals(200,response.get("status").asInt(),response.toString());
            assertEquals(200,call("/recipe/getById",Map.of("id",id),OWNER).get("status").asInt());
            assertEquals(200,call("/recipe/update",validBody(id),OWNER).get("status").asInt());
        } finally { removeRecipe(id); }
    }

    private Map<String,Object> validBody(String id) {
        Map<String,Object> body=new HashMap<>();
        body.put("recipe",Map.of("id",id,"recipeName","atomic-test-"+id,"recipeVersion","1.0"));
        body.put("recipeStepList",List.of(Map.of("stepNo",1,"stepDesc","replacement")));
        body.put("ingredientList",List.of()); body.put("imageList",List.of()); body.put("seasoningList",List.of());
        return body;
    }

    private String account(boolean family) {
        String id=UUID.randomUUID().toString();
        db.update("insert into app_user (id,username,nickname,email,birthday,status,user_role,remark,account_origin,auth_version) values (?,?,?,?,'1990-02-03','normal','user','private','HOUSEHOLD',0)",id,id,id,id+"@example.test");
        if (family) db.update("insert into household_member (id,household_id,user_id,member_role,status) values (?,?,?,'member','normal')",UUID.randomUUID().toString(),HOME,id);
        return id;
    }

    private String recipe(String owner) {
        String id=UUID.randomUUID().toString();
        db.update("insert into recipe (id,recipe_name,owner_user_id,visibility,status,recipe_version) values (?,?,?,'private','上架','1.0')",id,id,owner);
        return id;
    }

    private String[] children(String recipe) {
        String[] ids=new String[4];
        for(int i=0;i<ids.length;i++) ids[i]=UUID.randomUUID().toString();
        db.update("insert into recipe_step (id,recipe_id,step_no,step_desc) values (?,?,1,'original')",ids[0],recipe);
        db.update("insert into recipe_image (id,recipe_id,image_type,image_url) values (?,?,'cover','/test/image.jpg')",ids[1],recipe);
        db.update("insert into recipe_ingredient_rel (id,recipe_id,ingredient_id) values (?,?,'test-ingredient')",ids[2],recipe);
        db.update("insert into recipe_seasoning_rel (id,recipe_id,seasoning_id) values (?,?,'test-seasoning')",ids[3],recipe);
        return ids;
    }

    private void assertChildStatus(String recipe,String actor,int expected) throws Exception {
        for (String child : CHILDREN) {
            JsonNode response=call("/"+child+"/listByRecipeId",Map.of("recipeId",recipe),actor);
            assertEquals(expected,response.get("status").asInt(),child+": "+response);
            if (expected==200) assertEquals(1,response.get("data").size(),child);
        }
    }

    private void assertNoValue(JsonNode node,String... fields) {
        for (String field : fields) assertTrue(node.path(field).isMissingNode() || node.path(field).isNull(),field);
    }

    private void removeRecipe(String recipe) {
        for(String table:TABLES) db.update("delete from "+table+" where recipe_id=?",recipe);
        db.update("delete from recipe where id=?",recipe);
    }
}
