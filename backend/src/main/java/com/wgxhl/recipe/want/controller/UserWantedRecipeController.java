package com.wgxhl.recipe.want.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.common.dto.IdDTO;
import com.wgxhl.recipe.config.AuthRequestAttributes;
import com.wgxhl.recipe.user.entity.AppUser;
import com.wgxhl.recipe.want.dto.WantNotifyDTO;
import com.wgxhl.recipe.want.dto.WantedRecipePageDTO;
import com.wgxhl.recipe.want.dto.WantedRecipeUserRecipeDTO;
import com.wgxhl.recipe.want.entity.UserWantedRecipe;
import com.wgxhl.recipe.want.service.UserWantedRecipeService;
import com.wgxhl.recipe.want.vo.WantNotifyPreviewVO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/want")
public class UserWantedRecipeController {

    private final UserWantedRecipeService userWantedRecipeService;

    public UserWantedRecipeController(UserWantedRecipeService userWantedRecipeService) {
        this.userWantedRecipeService = userWantedRecipeService;
    }

    @PostMapping("/page")
    public ApiResponse<Page<UserWantedRecipe>> page(@RequestBody WantedRecipePageDTO dto) {
        return userWantedRecipeService.page(dto);
    }

    @PostMapping("/dateList")
    public ApiResponse<List<LocalDate>> dateList(@RequestBody WantedRecipePageDTO dto) {
        return userWantedRecipeService.dateList(dto.getUserId());
    }

    @PostMapping("/create")
    public ApiResponse<UserWantedRecipe> create(@RequestBody UserWantedRecipe entity) {
        return userWantedRecipeService.create(entity);
    }

    @PostMapping("/updateDate")
    public ApiResponse<Void> updatePlannedDate(@RequestBody UserWantedRecipe entity) {
        return userWantedRecipeService.updatePlannedDate(entity);
    }

    @PostMapping("/delete")
    public ApiResponse<Void> delete(@RequestBody IdDTO dto) {
        return userWantedRecipeService.delete(dto.getId());
    }

    @PostMapping("/deleteByRecipeId")
    public ApiResponse<Void> deleteByRecipeId(@RequestBody WantedRecipeUserRecipeDTO dto) {
        return userWantedRecipeService.deleteByRecipeId(dto.getUserId(), dto.getRecipeId());
    }

    @PostMapping("/check")
    public ApiResponse<Boolean> check(@RequestBody WantedRecipeUserRecipeDTO dto) {
        return userWantedRecipeService.check(dto.getUserId(), dto.getRecipeId());
    }

    @PostMapping("/notifyPreview")
    public ApiResponse<WantNotifyPreviewVO> notifyPreview(HttpServletRequest request) {
        AppUser currentUser = currentUser(request);
        return userWantedRecipeService.notifyPreview(currentUser);
    }

    @PostMapping("/notify")
    public ApiResponse<Integer> notify(@RequestBody WantNotifyDTO dto, HttpServletRequest request) {
        AppUser currentUser = currentUser(request);
        return userWantedRecipeService.notifyPrepare(currentUser, dto);
    }

    private AppUser currentUser(HttpServletRequest request) {
        Object value = request.getAttribute(AuthRequestAttributes.CURRENT_USER);
        return value instanceof AppUser ? (AppUser) value : null;
    }
}
