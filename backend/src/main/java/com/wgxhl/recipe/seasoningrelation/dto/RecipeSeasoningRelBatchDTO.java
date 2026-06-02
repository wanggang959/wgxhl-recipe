package com.wgxhl.recipe.seasoningrelation.dto;

import com.wgxhl.recipe.seasoningrelation.entity.RecipeSeasoningRel;
import lombok.Data;

import java.util.List;

@Data
public class RecipeSeasoningRelBatchDTO {

    private String recipeId;

    private List<RecipeSeasoningRel> relList;
}
