package com.wgxhl.recipe.todo.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.common.dto.IdDTO;
import com.wgxhl.recipe.config.AuthRequestAttributes;
import com.wgxhl.recipe.todo.dto.TodoPageDTO;
import com.wgxhl.recipe.todo.dto.TodoSummaryDTO;
import com.wgxhl.recipe.todo.entity.Todo;
import com.wgxhl.recipe.todo.service.TodoService;
import com.wgxhl.recipe.user.entity.AppUser;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/todo")
public class TodoController {

    private final TodoService todoService;

    public TodoController(TodoService todoService) {
        this.todoService = todoService;
    }

    @PostMapping("/page")
    public ApiResponse<Page<Todo>> page(@RequestBody TodoPageDTO dto, HttpServletRequest request) {
        return todoService.page(dto, currentUser(request));
    }

    @PostMapping("/upcoming")
    public ApiResponse<List<Todo>> upcoming(@RequestBody TodoPageDTO dto, HttpServletRequest request) {
        return todoService.upcoming((int) dto.getSize(), currentUser(request));
    }

    @PostMapping("/summary")
    public ApiResponse<TodoSummaryDTO> summary(HttpServletRequest request) {
        return todoService.summary(currentUser(request));
    }

    @PostMapping("/getById")
    public ApiResponse<Todo> getById(@RequestBody IdDTO dto, HttpServletRequest request) {
        return todoService.detail(dto.getId(), currentUser(request));
    }

    @PostMapping("/create")
    public ApiResponse<Todo> create(@RequestBody Todo entity, HttpServletRequest request) {
        return todoService.create(entity, currentUser(request));
    }

    @PostMapping("/update")
    public ApiResponse<Void> update(@RequestBody Todo entity, HttpServletRequest request) {
        return todoService.update(entity, currentUser(request));
    }

    @PostMapping("/complete")
    public ApiResponse<Void> complete(@RequestBody IdDTO dto, HttpServletRequest request) {
        return todoService.complete(dto.getId(), currentUser(request));
    }

    @PostMapping("/delete")
    public ApiResponse<Void> delete(@RequestBody IdDTO dto, HttpServletRequest request) {
        return todoService.delete(dto.getId(), currentUser(request));
    }

    private AppUser currentUser(HttpServletRequest request) {
        Object value = request.getAttribute(AuthRequestAttributes.CURRENT_USER);
        return value instanceof AppUser ? (AppUser) value : null;
    }
}
