package com.wgxhl.recipe.household.controller;

import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.common.dto.IdDTO;
import com.wgxhl.recipe.config.AuthRequestAttributes;
import com.wgxhl.recipe.config.JwtAuthUtil;
import com.wgxhl.recipe.household.dto.HouseholdCreateDTO;
import com.wgxhl.recipe.household.dto.HouseholdInviteCreateDTO;
import com.wgxhl.recipe.household.dto.HouseholdJoinDTO;
import com.wgxhl.recipe.household.dto.HouseholdTransferDTO;
import com.wgxhl.recipe.household.entity.Household;
import com.wgxhl.recipe.household.entity.HouseholdInvite;
import com.wgxhl.recipe.household.entity.HouseholdMember;
import com.wgxhl.recipe.household.service.HouseholdInviteService;
import com.wgxhl.recipe.household.service.HouseholdLeaveService;
import com.wgxhl.recipe.household.service.HouseholdMemberService;
import com.wgxhl.recipe.household.service.HouseholdService;
import com.wgxhl.recipe.household.vo.HouseholdInvitePreviewVO;
import com.wgxhl.recipe.household.vo.HouseholdVO;
import com.wgxhl.recipe.user.entity.AppUser;
import com.wgxhl.recipe.user.mapper.AppUserMapper;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/household")
public class HouseholdController {

    private final HouseholdService householdService;
    private final HouseholdMemberService householdMemberService;
    private final HouseholdInviteService householdInviteService;
    private final HouseholdLeaveService householdLeaveService;
    private final JwtAuthUtil jwtAuthUtil;
    private final AppUserMapper appUserMapper;

    public HouseholdController(HouseholdService householdService,
                               HouseholdMemberService householdMemberService,
                               HouseholdInviteService householdInviteService,
                               HouseholdLeaveService householdLeaveService,
                               JwtAuthUtil jwtAuthUtil,
                               AppUserMapper appUserMapper) {
        this.householdService = householdService;
        this.householdMemberService = householdMemberService;
        this.householdInviteService = householdInviteService;
        this.householdLeaveService = householdLeaveService;
        this.jwtAuthUtil = jwtAuthUtil;
        this.appUserMapper = appUserMapper;
    }

    @PostMapping("/me")
    public ApiResponse<HouseholdVO> me(HttpServletRequest request) {
        Household household = currentHousehold(request);
        HouseholdMember membership = currentMembership(request);
        if (household == null || membership == null) {
            return ApiResponse.fail(401, "请先登录");
        }
        return ApiResponse.success(toVO(household, membership, true));
    }

    @PostMapping("/list")
    public ApiResponse<List<HouseholdVO>> list(HttpServletRequest request) {
        AppUser user = currentUser(request);
        if (user == null) {
            return ApiResponse.fail(401, "请先登录");
        }
        Household active = currentHousehold(request);
        List<HouseholdMember> memberships = householdMemberService.listByUser(user.getId());
        if (memberships.isEmpty()) {
            return ApiResponse.success(Collections.emptyList());
        }
        List<String> householdIds = memberships.stream()
                .map(HouseholdMember::getHouseholdId)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        Map<String, Household> householdMap = householdService.listByIds(householdIds).stream()
                .collect(Collectors.toMap(Household::getId, Function.identity()));
        List<HouseholdVO> result = new ArrayList<>();
        for (HouseholdMember membership : memberships) {
            Household household = householdMap.get(membership.getHouseholdId());
            if (household == null || !"normal".equals(household.getStatus())) {
                continue;
            }
            result.add(toVO(household, membership, active != null && household.getId().equals(active.getId())));
        }
        return ApiResponse.success(result);
    }

    @PostMapping("/switch")
    public ApiResponse<AppUser> switchHousehold(@RequestBody IdDTO dto, HttpServletRequest request) {
        AppUser user = currentUser(request);
        if (user == null) {
            return ApiResponse.fail(401, "请先登录");
        }
        if (dto == null || !StringUtils.hasText(dto.getId())) {
            return ApiResponse.fail("家庭id不能为空");
        }
        HouseholdMember membership = householdMemberService.find(dto.getId(), user.getId());
        if (membership == null || !"normal".equals(membership.getStatus())) {
            return ApiResponse.fail(403, "你还不是该家庭的成员");
        }
        Household household = householdService.getById(dto.getId());
        if (household == null || !"normal".equals(household.getStatus())) {
            return ApiResponse.fail("家庭不存在或已停用");
        }
        return ApiResponse.success("已切换家庭", applyHousehold(user, household, membership));
    }

    @PostMapping("/create")
    public ApiResponse<AppUser> create(@RequestBody HouseholdCreateDTO dto, HttpServletRequest request) {
        AppUser user = currentUser(request);
        if (user == null) {
            return ApiResponse.fail(401, "请先登录");
        }
        if (isGuest(user)) {
            return ApiResponse.fail(403, "游客不能创建家庭");
        }
        String name = dto == null || dto.getHouseholdName() == null ? "" : dto.getHouseholdName().trim();
        if (!StringUtils.hasText(name)) {
            return ApiResponse.fail("家庭名称不能为空");
        }
        if (name.length() > 64) {
            return ApiResponse.fail("家庭名称不能超过64个字符");
        }
        if (householdService.lambdaQuery().eq(Household::getHouseholdName, name).exists()) {
            return ApiResponse.fail("家庭名称已存在");
        }
        Household household = new Household();
        household.setHouseholdName(name);
        household.setOwnerUserId(user.getId());
        householdService.save(household);

        HouseholdMember membership = new HouseholdMember();
        membership.setHouseholdId(household.getId());
        membership.setUserId(user.getId());
        membership.setMemberRole("owner");
        householdMemberService.save(membership);

        return ApiResponse.success("家庭已创建", applyHousehold(user, household, membership));
    }

    @PostMapping("/invite/create")
    public ApiResponse<HouseholdInvite> createInvite(@RequestBody HouseholdInviteCreateDTO dto,
                                                     HttpServletRequest request) {
        AppUser user = currentUser(request);
        Household household = currentHousehold(request);
        HouseholdMember membership = currentMembership(request);
        if (user == null || household == null || membership == null) {
            return ApiResponse.fail(401, "请先登录");
        }
        if (!canManage(membership)) {
            return ApiResponse.fail(403, "只有家庭管理员可以邀请成员");
        }
        HouseholdInvite invite = householdInviteService.createInvite(
                household.getId(),
                dto == null ? null : dto.getMemberRole(),
                dto == null ? null : dto.getMaxUses(),
                dto == null ? null : dto.getExpiresInHours(),
                user.getId()
        );
        return ApiResponse.success("邀请已创建", invite);
    }

    @PostMapping("/invite/list")
    public ApiResponse<List<HouseholdInvite>> listInvites(HttpServletRequest request) {
        Household household = currentHousehold(request);
        HouseholdMember membership = currentMembership(request);
        if (household == null || membership == null) {
            return ApiResponse.fail(401, "请先登录");
        }
        if (!canManage(membership)) {
            return ApiResponse.fail(403, "只有家庭管理员可以查看邀请");
        }
        return ApiResponse.success(householdInviteService.listActive(household.getId()));
    }

    @PostMapping("/invite/revoke")
    public ApiResponse<Void> revokeInvite(@RequestBody IdDTO dto, HttpServletRequest request) {
        Household household = currentHousehold(request);
        HouseholdMember membership = currentMembership(request);
        if (household == null || membership == null) {
            return ApiResponse.fail(401, "请先登录");
        }
        if (!canManage(membership)) {
            return ApiResponse.fail(403, "只有家庭管理员可以撤销邀请");
        }
        if (dto == null || !StringUtils.hasText(dto.getId())) {
            return ApiResponse.fail("邀请id不能为空");
        }
        if (!householdInviteService.revoke(dto.getId(), household.getId())) {
            return ApiResponse.fail("邀请不存在或已失效");
        }
        return ApiResponse.success("邀请已撤销", null);
    }

    @PostMapping("/invite/preview")
    public ApiResponse<HouseholdInvitePreviewVO> previewInvite(@RequestBody HouseholdJoinDTO dto,
                                                               HttpServletRequest request) {
        AppUser user = currentUser(request);
        if (user == null) {
            return ApiResponse.fail(401, "请先登录");
        }
        HouseholdInvite invite = householdInviteService.findUsable(dto == null ? null : dto.getInviteCode());
        if (invite == null) {
            return ApiResponse.fail("邀请码无效或已过期");
        }
        Household household = householdService.getById(invite.getHouseholdId());
        if (household == null || !"normal".equals(household.getStatus())) {
            return ApiResponse.fail("家庭不存在或已停用");
        }
        if (householdMemberService.find(household.getId(), user.getId()) != null) {
            return ApiResponse.fail("你已经是该家庭的成员");
        }
        HouseholdInvitePreviewVO vo = new HouseholdInvitePreviewVO();
        vo.setHouseholdName(household.getHouseholdName());
        vo.setMemberRole(invite.getMemberRole());
        vo.setInviterName(displayName(appUserMapper.selectById(invite.getCreatedBy())));
        vo.setExpiresAt(invite.getExpiresAt());
        vo.setMaxUses(invite.getMaxUses());
        vo.setUsedCount(invite.getUsedCount());
        return ApiResponse.success(vo);
    }

    @PostMapping("/join")
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<AppUser> join(@RequestBody HouseholdJoinDTO dto, HttpServletRequest request) {
        AppUser user = currentUser(request);
        if (user == null) {
            return ApiResponse.fail(401, "请先登录");
        }
        if (isGuest(user)) {
            return ApiResponse.fail(403, "游客不能加入家庭");
        }
        HouseholdInvite invite = householdInviteService.findUsable(dto == null ? null : dto.getInviteCode());
        if (invite == null) {
            return ApiResponse.fail("邀请码无效或已过期");
        }
        Household household = householdService.getById(invite.getHouseholdId());
        if (household == null || !"normal".equals(household.getStatus())) {
            return ApiResponse.fail("家庭不存在或已停用");
        }
        if (householdMemberService.find(household.getId(), user.getId()) != null) {
            return ApiResponse.fail("你已经是该家庭的成员");
        }
        if (!householdInviteService.consume(invite)) {
            return ApiResponse.fail("邀请码已被使用完");
        }
        HouseholdMember membership = new HouseholdMember();
        membership.setHouseholdId(household.getId());
        membership.setUserId(user.getId());
        membership.setMemberRole(invite.getMemberRole());
        householdMemberService.save(membership);

        return ApiResponse.success("已加入家庭", applyHousehold(user, household, membership));
    }

    @PostMapping("/leave")
    public ApiResponse<AppUser> leave(HttpServletRequest request) {
        AppUser user = currentUser(request);
        Household household = currentHousehold(request);
        HouseholdMember membership = currentMembership(request);
        if (user == null || household == null || membership == null) {
            return ApiResponse.fail(401, "请先登录");
        }
        return householdLeaveService.leave(user, household, membership);
    }

    @PostMapping("/transfer")
    public ApiResponse<AppUser> transfer(@RequestBody HouseholdTransferDTO dto, HttpServletRequest request) {
        AppUser user = currentUser(request);
        Household household = currentHousehold(request);
        HouseholdMember membership = currentMembership(request);
        if (user == null || household == null || membership == null) {
            return ApiResponse.fail(401, "请先登录");
        }
        return householdLeaveService.transfer(user, household, membership,
                dto == null ? null : dto.getTargetUserId());
    }

    @PostMapping("/dissolve")
    public ApiResponse<AppUser> dissolve(HttpServletRequest request) {
        AppUser user = currentUser(request);
        Household household = currentHousehold(request);
        HouseholdMember membership = currentMembership(request);
        if (user == null || household == null || membership == null) {
            return ApiResponse.fail(401, "请先登录");
        }
        return householdLeaveService.dissolve(user, household, membership);
    }

    private boolean canManage(HouseholdMember membership) {
        return membership != null
                && ("owner".equals(membership.getMemberRole()) || "admin".equals(membership.getMemberRole()));
    }

    private String displayName(AppUser user) {
        if (user == null) {
            return "家人";
        }
        return StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername();
    }

    private HouseholdVO toVO(Household household, HouseholdMember membership, boolean active) {
        HouseholdVO vo = new HouseholdVO();
        vo.setId(household.getId());
        vo.setHouseholdName(household.getHouseholdName());
        vo.setOwnerUserId(household.getOwnerUserId());
        vo.setMemberRole(membership.getMemberRole());
        vo.setMemberCount((long) householdMemberService.listUserIds(household.getId()).size());
        vo.setActive(active);
        return vo;
    }

    private AppUser applyHousehold(AppUser user, Household household, HouseholdMember membership) {
        user.setHouseholdId(household.getId());
        user.setHouseholdName(household.getHouseholdName());
        user.setMemberRole(membership.getMemberRole());
        user.setToken(jwtAuthUtil.createToken(user, household.getId()));
        user.setPassword(null);
        return user;
    }

    private AppUser currentUser(HttpServletRequest request) {
        Object value = request.getAttribute(AuthRequestAttributes.CURRENT_USER);
        return value instanceof AppUser ? (AppUser) value : null;
    }

    private Household currentHousehold(HttpServletRequest request) {
        Object value = request.getAttribute(AuthRequestAttributes.CURRENT_HOUSEHOLD);
        return value instanceof Household ? (Household) value : null;
    }

    private HouseholdMember currentMembership(HttpServletRequest request) {
        Object value = request.getAttribute(AuthRequestAttributes.CURRENT_MEMBERSHIP);
        return value instanceof HouseholdMember ? (HouseholdMember) value : null;
    }

    private boolean isGuest(AppUser user) {
        return user != null && ("guest".equals(user.getId()) || "guest".equals(user.getUsername()));
    }
}
