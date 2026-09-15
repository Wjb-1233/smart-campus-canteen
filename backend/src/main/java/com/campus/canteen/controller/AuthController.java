package com.campus.canteen.controller;

import com.campus.canteen.common.Result;
import com.campus.canteen.dto.HealthUpdateRequest;
import com.campus.canteen.dto.LoginRequest;
import com.campus.canteen.dto.RechargeRequest;
import com.campus.canteen.entity.BalanceLedger;
import com.campus.canteen.service.AuthService;
import com.campus.canteen.vo.UserProfileVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    @GetMapping("/profile")
    public Result<UserProfileVO> profile() {
        return Result.ok(authService.profile());
    }

    @PutMapping("/health")
    public Result<UserProfileVO> updateHealth(@RequestBody HealthUpdateRequest request) {
        return Result.ok(authService.updateHealth(request));
    }

    @PostMapping("/recharge")
    public Result<Map<String, Object>> recharge(@Valid @RequestBody RechargeRequest request) {
        return Result.ok(authService.recharge(request));
    }

    @GetMapping("/ledger")
    public Result<List<BalanceLedger>> ledger(@RequestParam(name = "limit", defaultValue = "20") int limit) {
        return Result.ok(authService.ledger(limit));
    }

    @GetMapping("/oauth/edu/authorize")
    public Result<Map<String, Object>> eduAuthorize(@RequestParam(name = "redirectUri", required = false) String redirectUri) {
        return Result.ok(authService.oauthAuthorizeUrl(redirectUri));
    }

    @GetMapping("/oauth/edu/callback")
    public Result<Map<String, Object>> eduCallback(@RequestParam(name = "code") String code,
                                                   @RequestParam(name = "state", required = false) String state) {
        return Result.ok(authService.oauthEduCallback(code, state));
    }
}
