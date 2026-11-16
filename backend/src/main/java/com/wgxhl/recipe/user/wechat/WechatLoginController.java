package com.wgxhl.recipe.user.wechat;

import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.config.ClientIpResolver;
import com.wgxhl.recipe.config.UserContext;
import com.wgxhl.recipe.user.dto.UserLoginDTO;
import com.wgxhl.recipe.user.entity.AppUser;
import com.wgxhl.recipe.user.service.AppUserService;
import com.wgxhl.recipe.user.service.LoginAttemptService;
import lombok.Data;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/user/wechat")
public class WechatLoginController {
    private final WechatIdentityClient wechat;
    private final JdbcTemplate db;
    private final AppUserService users;
    private final LoginAttemptService attempts;
    private final ClientIpResolver ips;
    public WechatLoginController(WechatIdentityClient wechat, JdbcTemplate db, AppUserService users,
                                 LoginAttemptService attempts, ClientIpResolver ips) {
        this.wechat = wechat; this.db = db; this.users = users; this.attempts = attempts; this.ips = ips;
    }
    @Data public static class LoginRequest { private String code; }
    @Data public static class BindRequest { private String code; private String username; private String password; }
    @Data public static class UnbindRequest { private String password; }
    @Data public static class BindingStatus {
        private final boolean bound;
    }

    @PostMapping("/status")
    public ApiResponse<BindingStatus> status() {
        AppUser current = UserContext.require();
        if (!StringUtils.hasText(wechat.appId())) return ApiResponse.fail("微信登录尚未配置");
        Integer count = db.queryForObject("select count(*) from user_wechat_binding where user_id=? and app_id=?",
                Integer.class, current.getId(), wechat.appId());
        return ApiResponse.success(new BindingStatus(count != null && count > 0));
    }

    @PostMapping("/unbind")
    public ApiResponse<Void> unbind(@RequestBody UnbindRequest request, HttpServletRequest http) {
        AppUser current = UserContext.require();
        if ("guest".equals(current.getId()) || "guest".equals(current.getUsername()) || UserContext.isGuest())
            return ApiResponse.fail(403, "游客不能解绑微信");
        if (!StringUtils.hasText(wechat.appId())) return ApiResponse.fail("微信登录尚未配置");
        if (!StringUtils.hasText(current.getPassword())) return ApiResponse.fail("请先设置账号密码，再解绑微信");
        if (request == null || !StringUtils.hasText(request.getPassword())) return ApiResponse.fail("请输入当前账号密码");
        UserLoginDTO credentials = new UserLoginDTO();
        credentials.setUsername(current.getUsername());
        credentials.setPassword(request.getPassword());
        ApiResponse<AppUser> verified = users.login(credentials, ips.resolve(http));
        if (!Integer.valueOf(200).equals(verified.getStatus())) return ApiResponse.fail(verified.getStatus(), verified.getMessage());
        if (verified.getData() == null || !current.getId().equals(verified.getData().getId()))
            return ApiResponse.fail(403, "请验证当前账号密码");
        db.update("delete from user_wechat_binding where user_id=? and app_id=?", current.getId(), wechat.appId());
        return ApiResponse.success(null);
    }
    @PostMapping("/login")
    public ApiResponse<AppUser> login(@RequestBody LoginRequest request, HttpServletRequest http) {
        String ip = ips.resolve(http);
        String bucket = "wechat-login:" + ip;
        String locked = attempts.lockedMessage(bucket, null);
        if (locked != null) return ApiResponse.fail(429, "微信登录尝试过于频繁，请稍后再试");
        try {
            String openId = wechat.exchange(request == null ? null : request.getCode());
            List<String> ids = db.query("select user_id from user_wechat_binding where app_id=? and open_id=?",
                    (rs, row) -> rs.getString(1), wechat.appId(), openId);
            if (ids.isEmpty()) {
                attempts.onFailure(bucket, null);
                return ApiResponse.fail(404, "微信尚未绑定，请先使用账号密码登录并绑定");
            }
            attempts.onSuccess(bucket, null);
            return users.loginWithWechatBinding(ids.get(0));
        } catch (IllegalArgumentException | IllegalStateException e) {
            attempts.onFailure(bucket, null);
            return ApiResponse.fail(e.getMessage());
        }
    }
    @PostMapping("/bind")
    public ApiResponse<Void> bind(@RequestBody BindRequest request, HttpServletRequest http) {
        AppUser current = UserContext.require();
        if ("guest".equals(current.getId()) || "guest".equals(current.getUsername()) || UserContext.isGuest())
            return ApiResponse.fail(403, "游客不能绑定微信");
        if (request == null) return ApiResponse.fail("参数不能为空");
        UserLoginDTO credentials = new UserLoginDTO();
        credentials.setUsername(request.getUsername());
        credentials.setPassword(request.getPassword());
        ApiResponse<AppUser> verified = users.login(credentials, ips.resolve(http));
        if (!Integer.valueOf(200).equals(verified.getStatus())) return ApiResponse.fail(verified.getStatus(), verified.getMessage());
        if (verified.getData() == null || !current.getId().equals(verified.getData().getId())) return ApiResponse.fail(403, "请验证当前账号密码");
        try {
            String openId = wechat.exchange(request.getCode());
            List<String> existing = db.query("select open_id from user_wechat_binding where user_id=? and app_id=?",
                    (rs, row) -> rs.getString(1), current.getId(), wechat.appId());
            if (!existing.isEmpty()) return existing.get(0).equals(openId) ? ApiResponse.success(null) : ApiResponse.fail("当前账号已绑定其他微信");
            db.update("insert into user_wechat_binding (user_id,app_id,open_id) values (?,?,?)", current.getId(), wechat.appId(), openId);
            return ApiResponse.success(null);
        } catch (DuplicateKeyException e) { return ApiResponse.fail("微信或账号已绑定，请勿重复绑定"); }
        catch (IllegalArgumentException | IllegalStateException e) { return ApiResponse.fail(e.getMessage()); }
    }
}
