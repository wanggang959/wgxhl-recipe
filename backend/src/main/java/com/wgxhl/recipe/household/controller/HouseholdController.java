package com.wgxhl.recipe.household.controller;

import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.config.AuthRequestAttributes;
import com.wgxhl.recipe.household.entity.Household;
import com.wgxhl.recipe.household.entity.HouseholdMember;
import com.wgxhl.recipe.household.service.HouseholdMemberService;
import com.wgxhl.recipe.household.vo.HouseholdVO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/household")
public class HouseholdController {

    private final HouseholdMemberService householdMemberService;

    public HouseholdController(HouseholdMemberService householdMemberService) {
        this.householdMemberService = householdMemberService;
    }

    @PostMapping("/me")
    public ApiResponse<HouseholdVO> me(HttpServletRequest request) {
        Object householdValue = request.getAttribute(AuthRequestAttributes.CURRENT_HOUSEHOLD);
        Object membershipValue = request.getAttribute(AuthRequestAttributes.CURRENT_MEMBERSHIP);
        if (!(householdValue instanceof Household) || !(membershipValue instanceof HouseholdMember)) {
            return ApiResponse.fail(401, "请先登录");
        }
        Household household = (Household) householdValue;
        HouseholdMember membership = (HouseholdMember) membershipValue;
        HouseholdVO vo = new HouseholdVO();
        vo.setId(household.getId());
        vo.setHouseholdName(household.getHouseholdName());
        vo.setOwnerUserId(household.getOwnerUserId());
        vo.setMemberRole(membership.getMemberRole());
        vo.setMemberCount((long) householdMemberService.listUserIds(household.getId()).size());
        return ApiResponse.success(vo);
    }
}
