package com.wgxhl.recipe.seasoning.dto;

import com.wgxhl.recipe.common.dto.PageDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class SeasoningPageDTO extends PageDTO {

    private String seasoningName;
}
