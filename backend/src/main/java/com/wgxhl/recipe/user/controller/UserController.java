package com.wgxhl.recipe.user.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.common.dto.IdDTO;
import com.wgxhl.recipe.user.dto.UserLoginDTO;
import com.wgxhl.recipe.user.dto.UserPageDTO;
import com.wgxhl.recipe.user.entity.AppUser;
import com.wgxhl.recipe.user.service.AppUserService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
public class UserController {

    private final AppUserService appUserService;

    public UserController(AppUserService appUserService) {
        this.appUserService = appUserService;
    }

    /***
     * @Description 分页查询用户
     * @Date 2026/06/01
     * @Author wg
     **/
    @PostMapping("/page")
    public ApiResponse<Page<AppUser>> page(@RequestBody UserPageDTO dto) {
        return appUserService.page(dto);
    }

    /***
     * @Description 根据id查询用户
     * @Date 2026/06/01
     * @Author wg
     **/
    @PostMapping("/getById")
    public ApiResponse<AppUser> getById(@RequestBody IdDTO dto) {
        return appUserService.getById(dto.getId());
    }

    /***
     * @Description 新增用户
     * @Date 2026/06/01
     * @Author wg
     **/
    @PostMapping("/create")
    public ApiResponse<AppUser> create(@RequestBody AppUser entity) {
        return appUserService.create(entity);
    }

    /***
     * @Description 更新用户
     * @Date 2026/06/01
     * @Author wg
     **/
    @PostMapping("/update")
    public ApiResponse<Void> update(@RequestBody AppUser entity) {
        return appUserService.update(entity);
    }

    /***
     * @Description 删除用户
     * @Date 2026/06/01
     * @Author wg
     **/
    @PostMapping("/delete")
    public ApiResponse<Void> delete(@RequestBody IdDTO dto) {
        return appUserService.delete(dto.getId());
    }

    /***
     * @Description 用户登录
     * @Date 2026/06/01
     * @Author wg
     **/
    @PostMapping("/login")
    public ApiResponse<AppUser> login(@RequestBody UserLoginDTO dto) {
        return appUserService.login(dto);
    }
}
