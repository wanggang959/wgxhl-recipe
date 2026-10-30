package com.wgxhl.recipe.recipe;

import com.wgxhl.recipe.favorite.mapper.UserFavoriteMapper;
import com.wgxhl.recipe.recipe.entity.Recipe;
import com.wgxhl.recipe.recipe.service.impl.RecipeServiceImpl;
import com.wgxhl.recipe.want.mapper.UserWantedRecipeMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

class RecipeGuestStateTest {
    @Test
    void guestDoesNotQueryPersonalState() {
        UserFavoriteMapper favorites = mock(UserFavoriteMapper.class);
        UserWantedRecipeMapper wanted = mock(UserWantedRecipeMapper.class);
        RecipeServiceImpl service = new RecipeServiceImpl(null, null, null, null, null, favorites,
                null, wanted, null, null, null, null, null, null);
        Recipe recipe = new Recipe(); recipe.setId("recipe");
        recipe.setFavorite(true); recipe.setWanted(true);
        ReflectionTestUtils.invokeMethod(service, "fillPersonalStates", Collections.singletonList(recipe), "guest");
        assertFalse(recipe.getFavorite());
        assertFalse(recipe.getWanted());
        verifyNoInteractions(favorites, wanted);
    }
}
