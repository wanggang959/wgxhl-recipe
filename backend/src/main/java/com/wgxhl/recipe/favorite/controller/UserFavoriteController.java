package com.wgxhl.recipe.favorite.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.common.dto.IdDTO;
import com.wgxhl.recipe.favorite.dto.FavoritePageDTO;
import com.wgxhl.recipe.favorite.dto.FavoriteUserRecipeDTO;
import com.wgxhl.recipe.favorite.entity.UserFavorite;
import com.wgxhl.recipe.favorite.service.UserFavoriteService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/favorite")
public class UserFavoriteController {

    private final UserFavoriteService userFavoriteService;

    public UserFavoriteController(UserFavoriteService userFavoriteService) {
        this.userFavoriteService = userFavoriteService;
    }

    /***
     * @Description 分页查询收藏
     * @Date 2026/06/01
     * @Author wg
     **/
    @PostMapping("/page")
    public ApiResponse<Page<UserFavorite>> page(@RequestBody FavoritePageDTO dto) {
        return userFavoriteService.page(dto);
    }

    /***
     * @Description 新增收藏
     * @Date 2026/06/01
     * @Author wg
     **/
    @PostMapping("/create")
    public ApiResponse<UserFavorite> create(@RequestBody UserFavorite entity) {
        return userFavoriteService.create(entity);
    }

    /***
     * @Description 删除收藏
     * @Date 2026/06/01
     * @Author wg
     **/
    @PostMapping("/delete")
    public ApiResponse<Void> delete(@RequestBody IdDTO dto) {
        return userFavoriteService.delete(dto.getId());
    }

    /***
     * @Description 按用户和菜谱取消收藏
     * @Date 2026/06/01
     * @Author wg
     **/
    @PostMapping("/deleteByRecipeId")
    public ApiResponse<Void> deleteByRecipeId(@RequestBody FavoriteUserRecipeDTO dto) {
        return userFavoriteService.deleteByRecipeId(dto.getUserId(), dto.getRecipeId());
    }

    /***
     * @Description 检查是否已收藏
     * @Date 2026/06/01
     * @Author wg
     **/
    @PostMapping("/check")
    public ApiResponse<Boolean> check(@RequestBody FavoriteUserRecipeDTO dto) {
        return userFavoriteService.check(dto.getUserId(), dto.getRecipeId());
    }
}
