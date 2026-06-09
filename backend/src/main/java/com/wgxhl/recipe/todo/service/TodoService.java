package com.wgxhl.recipe.todo.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.todo.dto.TodoPageDTO;
import com.wgxhl.recipe.todo.dto.TodoSummaryDTO;
import com.wgxhl.recipe.todo.entity.Todo;
import com.wgxhl.recipe.user.entity.AppUser;

import java.util.List;

public interface TodoService extends IService<Todo> {

    ApiResponse<Page<Todo>> page(TodoPageDTO dto, AppUser actor);

    ApiResponse<List<Todo>> upcoming(int limit, AppUser actor);

    ApiResponse<TodoSummaryDTO> summary(AppUser actor);

    ApiResponse<Todo> detail(String id, AppUser actor);

    ApiResponse<Todo> create(Todo entity, AppUser actor);

    ApiResponse<Void> update(Todo entity, AppUser actor);

    ApiResponse<Void> complete(String id, AppUser actor);

    ApiResponse<Void> delete(String id, AppUser actor);

    void syncBirthdayTodo(AppUser user);

    int scanAndNotify();
}
