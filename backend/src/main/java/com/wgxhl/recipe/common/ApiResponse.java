package com.wgxhl.recipe.common;

import lombok.Data;

@Data
public class ApiResponse<T> {

    private Integer status;

    private String message;

    private T data;

    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setStatus(200);
        response.setMessage("success");
        response.setData(data);
        return response;
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setStatus(200);
        response.setMessage(message);
        response.setData(data);
        return response;
    }

    public static <T> ApiResponse<T> fail(String message) {
        return fail(500, message);
    }

    public static <T> ApiResponse<T> fail(Integer status, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setStatus(status);
        response.setMessage(message);
        return response;
    }
}
