package com.procurement.module.auth.controller;

import com.procurement.common.result.Result;
import com.procurement.module.auth.dto.LoginRequest;
import com.procurement.module.auth.dto.LoginResponse;
import com.procurement.module.auth.service.AuthService;
import com.procurement.module.system.service.UserService;
import com.procurement.module.system.vo.UserVO;
import com.procurement.security.SecurityUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        authService.logout(request.getHeader("Authorization"));
        return Result.ok();
    }

    @GetMapping("/profile")
    public Result<UserVO> profile(@AuthenticationPrincipal SecurityUser user) {
        return Result.ok(userService.getById(user.getUserId()));
    }

    @PutMapping("/password")
    public Result<Void> changePassword(@AuthenticationPrincipal SecurityUser user,
                                       @RequestParam String oldPassword,
                                       @RequestParam String newPassword) {
        authService.changePassword(user.getUserId(), oldPassword, newPassword);
        return Result.ok();
    }
}
