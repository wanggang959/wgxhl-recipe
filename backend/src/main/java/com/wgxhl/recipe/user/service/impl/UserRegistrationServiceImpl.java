package com.wgxhl.recipe.user.service.impl;

import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.config.JwtAuthUtil;
import com.wgxhl.recipe.household.entity.Household;
import com.wgxhl.recipe.household.entity.HouseholdInvite;
import com.wgxhl.recipe.household.entity.HouseholdMember;
import com.wgxhl.recipe.household.service.HouseholdInviteService;
import com.wgxhl.recipe.household.service.HouseholdMemberService;
import com.wgxhl.recipe.household.service.HouseholdService;
import com.wgxhl.recipe.user.dto.RegisterEmailDTO;
import com.wgxhl.recipe.user.dto.RegisterInviteDTO;
import com.wgxhl.recipe.user.entity.AppUser;
import com.wgxhl.recipe.user.mapper.AppUserMapper;
import com.wgxhl.recipe.user.service.AppUserService;
import com.wgxhl.recipe.user.service.EmailVerificationCodeService;
import com.wgxhl.recipe.user.service.UserRegistrationService;
import com.wgxhl.recipe.user.util.DefaultAvatars;
import com.wgxhl.recipe.user.util.PasswordUtil;
import com.wgxhl.recipe.user.vo.RegisterInviteInfoVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class UserRegistrationServiceImpl implements UserRegistrationService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[\\p{L}\\p{N}_-]{2,32}$");
    private static final Set<String> RESERVED_USERNAMES = new HashSet<>(Arrays.asList(
            "guest", "王师傅", "admin-wangshifu"
    ));

    private final EmailVerificationCodeService emailVerificationCodeService;
    private final AppUserService appUserService;
    private final AppUserMapper appUserMapper;
    private final HouseholdService householdService;
    private final HouseholdMemberService householdMemberService;
    private final HouseholdInviteService householdInviteService;
    private final JwtAuthUtil jwtAuthUtil;

    public UserRegistrationServiceImpl(EmailVerificationCodeService emailVerificationCodeService,
                                       AppUserService appUserService,
                                       AppUserMapper appUserMapper,
                                       HouseholdService householdService,
                                       HouseholdMemberService householdMemberService,
                                       HouseholdInviteService householdInviteService,
                                       JwtAuthUtil jwtAuthUtil) {
        this.emailVerificationCodeService = emailVerificationCodeService;
        this.appUserService = appUserService;
        this.appUserMapper = appUserMapper;
        this.householdService = householdService;
        this.householdMemberService = householdMemberService;
        this.householdInviteService = householdInviteService;
        this.jwtAuthUtil = jwtAuthUtil;
    }

    @Override
    public ApiResponse<Void> sendRegisterCode(String email) {
        return emailVerificationCodeService.sendRegisterCode(email);
    }

    @Override
    public ApiResponse<RegisterInviteInfoVO> inviteInfo(String inviteCode) {
        HouseholdInvite invite = householdInviteService.findUsable(inviteCode);
        if (invite == null) {
            return ApiResponse.fail("邀请码无效或已过期");
        }
        Household household = householdService.getById(invite.getHouseholdId());
        if (household == null || !"normal".equals(household.getStatus())) {
            return ApiResponse.fail("家庭不存在或已停用");
        }
        RegisterInviteInfoVO vo = new RegisterInviteInfoVO();
        vo.setHouseholdName(household.getHouseholdName());
        vo.setMemberRole(invite.getMemberRole());
        AppUser inviter = appUserMapper.selectById(invite.getCreatedBy());
        vo.setInviterName(inviter == null ? "家人"
                : (StringUtils.hasText(inviter.getNickname()) ? inviter.getNickname() : inviter.getUsername()));
        return ApiResponse.success(vo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<AppUser> registerByEmail(RegisterEmailDTO dto) {
        if (dto == null) {
            return ApiResponse.fail("注册信息不能为空");
        }
        String email = normalizeEmail(dto.getEmail());
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            return ApiResponse.fail("请输入有效的邮箱地址");
        }
        String username = normalizeText(dto.getUsername());
        String validationError = validateAccount(username, dto.getPassword(), dto.getNickname());
        if (validationError != null) {
            return ApiResponse.fail(validationError);
        }
        if (appUserService.lambdaQuery().eq(AppUser::getEmail, email).exists()) {
            return ApiResponse.fail("该邮箱已注册，请直接登录");
        }
        if (appUserService.lambdaQuery().eq(AppUser::getUsername, username).exists()) {
            return ApiResponse.fail("用户名已存在");
        }
        String codeError = emailVerificationCodeService.verifyAndConsume(email, dto.getCode());
        if (codeError != null) {
            return ApiResponse.fail(codeError);
        }
        return createAccount(username, dto.getNickname(), dto.getPassword(), email, dto.getInviteCode(), true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<AppUser> registerByInvite(RegisterInviteDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getInviteCode())) {
            return ApiResponse.fail("请输入邀请码");
        }
        String username = normalizeText(dto.getUsername());
        String validationError = validateAccount(username, dto.getPassword(), dto.getNickname());
        if (validationError != null) {
            return ApiResponse.fail(validationError);
        }
        if (appUserService.lambdaQuery().eq(AppUser::getUsername, username).exists()) {
            return ApiResponse.fail("用户名已存在");
        }
        return createAccount(username, dto.getNickname(), dto.getPassword(), null, dto.getInviteCode(), false);
    }

    private ApiResponse<AppUser> createAccount(String username, String nickname, String password,
                                               String email, String inviteCode, boolean emailMode) {
        HouseholdInvite invite = null;
        Household household;
        if (StringUtils.hasText(inviteCode)) {
            invite = householdInviteService.findUsable(inviteCode);
            if (invite == null) {
                return ApiResponse.fail("邀请码无效或已过期");
            }
            household = householdService.getById(invite.getHouseholdId());
            if (household == null || !"normal".equals(household.getStatus())) {
                return ApiResponse.fail("家庭不存在或已停用");
            }
        } else {
            household = null;
        }

        AppUser user = new AppUser();
        user.setUsername(username);
        user.setNickname(StringUtils.hasText(nickname) ? nickname.trim() : username);
        user.setPassword(PasswordUtil.hash(password));
        user.setEmail(email);
        user.setUserRole("user");
        user.setStatus("normal");
        user.setAvatar(DefaultAvatars.pickForSeed(username));
        user.setNotificationPreference(emailMode ? "site,email" : "site");
        if (!appUserService.save(user)) {
            markRollback();
            return ApiResponse.fail("注册失败，请稍后重试");
        }

        if (invite != null) {
            if (!householdInviteService.consume(invite)) {
                markRollback();
                return ApiResponse.fail("邀请码已被使用完");
            }
            HouseholdMember membership = new HouseholdMember();
            membership.setHouseholdId(household.getId());
            membership.setUserId(user.getId());
            membership.setMemberRole(invite.getMemberRole());
            householdMemberService.save(membership);

            user.setHouseholdId(household.getId());
            user.setHouseholdName(household.getHouseholdName());
            user.setMemberRole(membership.getMemberRole());
            user.setToken(jwtAuthUtil.createToken(user, household.getId()));
            user.setPassword(null);
            return ApiResponse.success("注册成功", user);
        }

        Household personal = createPersonalHousehold(user);
        HouseholdMember membership = new HouseholdMember();
        membership.setHouseholdId(personal.getId());
        membership.setUserId(user.getId());
        membership.setMemberRole("owner");
        householdMemberService.save(membership);

        user.setHouseholdId(personal.getId());
        user.setHouseholdName(personal.getHouseholdName());
        user.setMemberRole("owner");
        user.setToken(jwtAuthUtil.createToken(user, personal.getId()));
        user.setPassword(null);
        return ApiResponse.success("注册成功", user);
    }

    private Household createPersonalHousehold(AppUser user) {
        String baseName = StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername();
        String name = baseName + "的家";
        if (householdService.lambdaQuery().eq(Household::getHouseholdName, name).exists()) {
            name = name + "-" + user.getId().substring(0, Math.min(4, user.getId().length()));
        }
        Household household = new Household();
        household.setHouseholdName(name);
        household.setOwnerUserId(user.getId());
        householdService.save(household);
        return household;
    }

    private String validateAccount(String username, String password, String nickname) {
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            return "用户名需为 2-32 位中文、字母、数字、下划线或短横线";
        }
        if (RESERVED_USERNAMES.contains(username.toLowerCase())) {
            return "该用户名为系统保留";
        }
        if (!StringUtils.hasText(password) || password.length() < 8 || password.length() > 64) {
            return "密码长度需为 8-64 位";
        }
        if (StringUtils.hasText(nickname) && nickname.trim().length() > 64) {
            return "昵称不能超过 64 个字符";
        }
        return null;
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private String normalizeText(String text) {
        return text == null ? "" : text.trim();
    }

    private void markRollback() {
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
    }
}
