package com.wgxhl.recipe.recipe;

import com.wgxhl.recipe.config.TenantContext;
import com.wgxhl.recipe.config.UserContext;
import com.wgxhl.recipe.recipe.entity.Recipe;
import com.wgxhl.recipe.favorite.entity.UserFavorite;
import com.wgxhl.recipe.favorite.service.UserFavoriteService;
import com.wgxhl.recipe.recipe.dto.RecipePageDTO;
import com.wgxhl.recipe.recipe.service.RecipeService;
import com.wgxhl.recipe.user.entity.AppUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("integration")
@EnabledIfSystemProperty(named = "test.integration.enabled", matches = "true")
class RecipeIntegrationTest {
    private static final String HOME = "00000000-0000-0000-0000-000000000001";
    private static final String USER = "admin-wangshifu";

    @Autowired private RecipeService recipes;
    @Autowired private UserFavoriteService favorites;
    @Autowired private JdbcTemplate jdbc;
    private String id;
    private String privateId;

    @BeforeEach
    void setup() {
        id = UUID.randomUUID().toString();
        privateId = UUID.randomUUID().toString();
        jdbc.update("insert into recipe (id, recipe_name, recipe_version, owner_user_id, visibility, status, like_count) values (?, ?, '1.0', ?, 'public', '上架', 0)", id, id, USER);
        jdbc.update("insert into recipe (id, recipe_name, recipe_version, owner_user_id, visibility, status, like_count) values (?, ?, '1.0', 'other-user', 'private', '上架', 0)", privateId, privateId);
        setContext();
    }

    private void setContext() {
        AppUser user = new AppUser();
        user.setId(USER);
        UserContext.set(user);
        TenantContext.set(HOME);
    }

    @AfterEach
    void cleanup() {
        UserContext.clear();
        TenantContext.clear();
        jdbc.update("delete from recipe_like where recipe_id in (?, ?)", id, privateId);
        jdbc.update("delete from user_favorite where recipe_id in (?, ?)", id, privateId);
        jdbc.update("delete from user_wanted_recipe where recipe_id in (?, ?)", id, privateId);
        jdbc.update("delete from recipe where id in (?, ?)", id, privateId);
    }

    @Test
    void simultaneousTogglesStayConsistent() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        try {
            java.util.concurrent.Callable<Integer> toggle = () -> {
                setContext();
                try {
                    barrier.await(10, TimeUnit.SECONDS);
                    return recipes.toggleLike(id).getStatus();
                } finally {
                    UserContext.clear();
                    TenantContext.clear();
                }
            };
            Future<Integer> first = pool.submit(toggle);
            Future<Integer> second = pool.submit(toggle);
            assertEquals(200, first.get(15, TimeUnit.SECONDS));
            assertEquals(200, second.get(15, TimeUnit.SECONDS));
            assertEquals(0, jdbc.queryForObject("select like_count from recipe where id = ?", Integer.class, id));
            assertEquals(0, jdbc.queryForObject("select count(*) from recipe_like where recipe_id = ?", Integer.class, id));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void batchReturnsOnlyVisibleRecipesAndCurrentFamilyStates() {
        jdbc.update("insert into user_favorite (id, household_id, user_id, recipe_id) values (?, ?, ?, ?)", UUID.randomUUID().toString(), HOME, USER, id);
        jdbc.update("insert into user_favorite (id, household_id, user_id, recipe_id) values (?, 'another-home', 'another-user', ?)", UUID.randomUUID().toString(), id);
        jdbc.update("insert into user_wanted_recipe (id, household_id, user_id, recipe_id, planned_date) values (?, ?, ?, ?, current_date())", UUID.randomUUID().toString(), HOME, USER, id);
        List<Recipe> result = recipes.batch(Arrays.asList(id, privateId)).getData();
        assertEquals(Arrays.asList(id), result.stream().map(Recipe::getId).collect(Collectors.toList()));
        assertTrue(result.get(0).getFavorite());
        assertTrue(result.get(0).getWanted());
        assertEquals(2L, result.get(0).getFavoriteCount());

        RecipePageDTO query = new RecipePageDTO();
        query.setCurrent(1L);
        query.setSize(20L);
        query.setScope("public");
        query.setRecipeName(id);
        Recipe listed = recipes.page(query).getData().getRecords().get(0);
        assertTrue(listed.getFavorite());
        assertTrue(listed.getWanted());
        assertEquals(2L, listed.getFavoriteCount());
    }

    @Test
    void favoriteCountsAndPermissionsMatchDetailVisibility() {
        UserFavorite favorite = new UserFavorite();
        favorite.setRecipeId(privateId);
        assertNotEquals(200, favorites.create(favorite).getStatus());
        assertNotEquals(200, recipes.getDetailById(privateId).getStatus());

        jdbc.update("update recipe set owner_user_id='other-user', status='下架' where id=?", id);
        favorite.setRecipeId(id);
        assertNotEquals(200, favorites.create(favorite).getStatus());
        assertNotEquals(200, recipes.getDetailById(id).getStatus());

        jdbc.update("update recipe set owner_user_id=?, status='下架' where id=?", USER, id);
        assertEquals(200, recipes.getDetailById(id).getStatus());
        assertEquals(1L, favorites.create(favorite).getData().getFavoriteCount());
        assertEquals(0L, favorites.deleteByRecipeId(USER, id).getData().getFavoriteCount());
    }

    @Test
    void pagingIsBoundedAndStableWhenTimestampsMatch() {
        String prefix = UUID.randomUUID().toString();
        try {
            for (int i = 0; i < 60; i++) {
                jdbc.update("insert into recipe (id, recipe_name, recipe_version, owner_user_id, visibility, status, like_count, create_time) values (?, ?, '1.0', ?, 'public', '上架', 0, '2026-01-01 12:00:00')",
                        UUID.randomUUID().toString(), prefix + i, USER);
            }
            RecipePageDTO query = new RecipePageDTO();
            query.setScope("public"); query.setRecipeName(prefix); query.setCurrent(1L); query.setSize(100_000L);
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<Recipe> first = recipes.page(query).getData();
            assertEquals(50, first.getRecords().size());
            assertEquals(60, first.getTotal());
            query.setCurrent(2L);
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<Recipe> second = recipes.page(query).getData();
            assertEquals(10, second.getRecords().size());
            assertEquals(60, java.util.stream.Stream.concat(first.getRecords().stream(), second.getRecords().stream())
                    .map(Recipe::getId).distinct().count());
        } finally {
            jdbc.update("delete from recipe where recipe_name like ?", prefix + "%");
        }
    }
}
