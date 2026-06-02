# Recipe Backend 后端开发规范

## 一、项目基础信息

### 项目名称

```text
recipe-backend
```

### 基础包名

```java
com.wgxhl.recipe
```

模块示例：

```java
com.wgxhl.recipe.recipe
com.wgxhl.recipe.category
com.wgxhl.recipe.ingredient
com.wgxhl.recipe.common
```

---

# 二、运行与基础配置

## 统一上下文路径

```yaml
server:
  port: ${APP_PORT:9001}
  servlet:
    context-path: /recipe
```

最终接口：

```text
/recipe/category/page

/recipe/recipe/page

/recipe/recipe/create
```

---

## MySQL配置

```yaml
spring:
  datasource:
    url: jdbc:mysql://${DB_HOST:127.0.0.1}:3306/recipe?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: ${DB_USERNAME:recipe}
    password: ${DB_PASSWORD:123456}
```

---

# 三、统一返回结构

## ApiResponse

位置：

```java
com.wgxhl.recipe.common.ApiResponse
```

结构：

```java
public class ApiResponse<T> {

    private Integer status;

    private String message;

    private T data;
}
```

---

## Controller统一返回

成功：

```java
ApiResponse.success(data);

ApiResponse.success("保存成功", data);
```

失败：

```java
ApiResponse.fail("保存失败");

ApiResponse.fail(500,"保存失败");
```

---

# 四、数据库规范

遵循：

《MySQL企业级DDL生成规范》

核心要求：

```text
uuid主键

无外键

关系表控制

小写下划线命名

utf8mb4

datetime

comment完整
```

---

# 五、实体(Entity)规范

## 包结构

```java
com.wgxhl.recipe.recipe.entity

com.wgxhl.recipe.category.entity

com.wgxhl.recipe.ingredient.entity
```

---

## TableName规范

```java
@TableName("recipe")
public class Recipe {
}
```

```java
@TableName("recipe_category")
public class RecipeCategory {
}
```

---

## 主键规范

```java
@TableId(
    value = "id",
    type = IdType.INPUT
)
private String id;
```

---

## 字段命名规范

数据库：

```text
recipe_name

cover_image

create_time
```

实体：

```java
private String recipeName;

private String coverImage;

private LocalDateTime createTime;
```

统一采用：

```text
数据库：小写下划线

Java：驼峰命名
```

---

## 字段注释规范

统一使用：

```java
/*
 * 菜谱名称
 */
private String recipeName;

/*
 * 菜谱描述
 */
private String recipeDesc;

/*
 * 成品图片
 */
private String coverImage;

/*
 * 创建时间
 */
private LocalDateTime createTime;
```

---

# 六、Mapper规范

包：

```java
com.wgxhl.recipe.recipe.mapper
```

示例：

```java
@Mapper
public interface RecipeMapper
        extends BaseMapper<Recipe> {

}
```

规则：

* 所有Mapper继承BaseMapper
* 非必要不写XML
* 优先MyBatis-Plus

---

# 七、Service规范

接口：

```java
public interface RecipeService
        extends IService<Recipe> {

}
```

实现：

```java
@Service
public class RecipeServiceImpl
        extends ServiceImpl<
                RecipeMapper,
                Recipe>
        implements RecipeService {

}
```

---

## UUID生成规范

统一在save中生成：

```java
@Override
public boolean save(Recipe entity) {

    if (!StringUtils.hasText(entity.getId())) {
        entity.setId(IdUtil.simpleUUID());
    }

    return super.save(entity);
}
```

禁止：

```text
controller生成id
```

必须：

```text
service层生成
```

---

## 时间字段维护规范

统一在Service层维护：

```java
@Override
public boolean save(Recipe entity) {

    if (!StringUtils.hasText(entity.getId())) {
        entity.setId(IdUtil.simpleUUID());
    }

    LocalDateTime now = LocalDateTime.now();

    entity.setCreateTime(now);
    entity.setUpdateTime(now);

    return super.save(entity);
}
```

修改时：

```java
entity.setUpdateTime(LocalDateTime.now());
```

禁止：

```text
数据库触发器

on update current_timestamp
```

---

# 八、Controller规范

## 包结构

```java
com.wgxhl.recipe.recipe.controller
```

---

## 类注解

```java
@RestController
@RequestMapping("/recipe")
```

---

## 接口规范

统一：

```java
@PostMapping("/page")

@PostMapping("/getById")

@PostMapping("/create")

@PostMapping("/update")

@PostMapping("/delete")
```

---

## 方法注释规范

```java
/***
 * @Description 分页查询菜谱
 * @Date 2026/06/01
 * @Author wg
 **/
@PostMapping("/page")
public Page<Recipe> page(...) {

}
```

---

# 九、文件上传规范

## 存储方案

统一使用：

```text
腾讯云COS
```

后续可切换：

```text
MinIO

阿里云OSS
```

统一通过：

```java
FileStorageService
```

进行封装。

---

## 图片分类目录

菜谱封面：

```text
recipe/cover/
```

步骤图片：

```text
recipe/step/
```

食材图片：

```text
ingredient/
```

用户头像：

```text
user/avatar/
```

---

# 十、业务模块划分

## category

菜谱分类

例如：

```text
川菜

粤菜

湘菜

早餐

甜品

饮品
```

---

## recipe

菜谱管理

包含：

```text
菜谱名称

菜谱简介

封面图

分类

浏览量

收藏量
```

---

## recipe_step

制作步骤

包含：

```text
步骤序号

步骤描述

步骤图片
```

---

## ingredient

食材管理

包含：

```text
食材名称

食材图片

食材描述
```

---

## recipe_ingredient_rel

菜谱食材关系

包含：

```text
菜谱ID

食材ID

用量

单位
```

---

## user_favorite

用户收藏

包含：

```text
用户ID

菜谱ID
```

---

# 十一、唯一性校验规范

新增：

```java
lambdaQuery()
.eq(Recipe::getRecipeName,name)
```

修改：

```java
lambdaQuery()
.ne(Recipe::getId,id)
.eq(Recipe::getRecipeName,name)
```

失败返回：

```java
ApiResponse.fail("菜谱名称已存在");
```

---

# 十二、分页规范

统一使用MyBatis-Plus：

```java
Page<Recipe> page =
        new Page<>(current,size);
```

返回：

```java
Page<Recipe>
```

禁止自定义分页结构。

---

# 十三、代码生成规范

生成代码时默认生成：

```text
DDL

Entity

Mapper

Service

ServiceImpl

Controller

DTO

VO
```

目录结构：

```text
entity
mapper
service
service/impl
controller
dto
vo
```

---

# 十四、项目统一原则

```text
Spring Boot

MyBatis-Plus

MySQL

UUID主键

ApiResponse统一返回

Hutool simpleUUID

小写下划线表名

驼峰实体字段

无外键

关系表控制

腾讯云COS存储图片

统一Service生成ID

统一Service维护时间字段
```
