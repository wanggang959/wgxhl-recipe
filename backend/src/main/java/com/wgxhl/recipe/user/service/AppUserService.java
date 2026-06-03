package com.wgxhl.recipe.user.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.user.dto.UserLoginDTO;
import com.wgxhl.recipe.user.dto.UserPageDTO;
import com.wgxhl.recipe.user.entity.AppUser;

public interface AppUserService extends IService<AppUser> {

    ApiResponse<Page<AppUser>> page(UserPageDTO dto);

    ApiResponse<AppUser> getById(String id);

    ApiResponse<AppUser> create(AppUser entity);

    ApiResponse<Void> update(AppUser entity);

    ApiResponse<Void> delete(String id);

    ApiResponse<AppUser> login(UserLoginDTO dto);

    ApiResponse<AppUser> guestLogin();
}
