package com.wgxhl.recipe.favorite.service.impl;

import com.wgxhl.recipe.config.UserContext;
import com.wgxhl.recipe.favorite.entity.UserFavorite;
import com.wgxhl.recipe.favorite.mapper.UserFavoriteMapper;
import com.wgxhl.recipe.favorite.mapper.FavoriteCount;
import com.wgxhl.recipe.recipe.mapper.RecipeMapper;
import com.wgxhl.recipe.recipe.service.RecipeService;
import com.wgxhl.recipe.user.entity.AppUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class UserFavoriteServiceImplTest {
    private final UserFavoriteMapper mapper = mock(UserFavoriteMapper.class);
    private final RecipeMapper recipeMapper = mock(RecipeMapper.class);
    private final RecipeService recipes = mock(RecipeService.class);
    private final UserFavoriteServiceImpl service = new UserFavoriteServiceImpl(recipeMapper, recipes);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        AppUser user = new AppUser();
        user.setId("current-user");
        UserContext.set(user);
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void cannotDeleteAnotherUsersFavorite() {
        UserFavorite favorite = new UserFavorite();
        favorite.setUserId("another-user");
        when(mapper.selectById("favorite-id")).thenReturn(favorite);

        assertEquals(500, service.delete("favorite-id").getStatus());
        verify(mapper, never()).deleteById(anyString());
    }

    @Test
    void createIgnoresSuppliedUserId() {
        UserFavorite favorite = new UserFavorite();
        favorite.setUserId("another-user");

        assertEquals(500, service.create(favorite).getStatus());
        assertEquals("current-user", favorite.getUserId());
        verify(mapper, never()).insert(any(UserFavorite.class));
    }

    @Test
    void deletingLastFavoriteReturnsZero() {
        when(mapper.delete(any())).thenReturn(1);
        when(mapper.countByRecipeIds(Collections.singletonList("recipe-1"))).thenReturn(Collections.emptyList());

        FavoriteCount count = service.deleteByRecipeId("ignored", "recipe-1").getData();
        assertEquals("recipe-1", count.getRecipeId());
        assertEquals(0L, count.getFavoriteCount());
    }
}
