package com.wgxhl.recipe.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @NonNull
    private final AdminPermissionInterceptor adminPermissionInterceptor;

    public WebMvcConfig(@NonNull AdminPermissionInterceptor adminPermissionInterceptor) {
        this.adminPermissionInterceptor = adminPermissionInterceptor;
    }

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(adminPermissionInterceptor)
                .addPathPatterns("/**");
    }
}
