package fun.medrec.spring.controller;

import fun.medrec.spring.domain.common.Result;
import fun.medrec.spring.domain.dto.LoginData;
import fun.medrec.spring.domain.entity.User;
import fun.medrec.spring.service.UserService;
import fun.medrec.spring.utils.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/login")
@Slf4j
public class LoginController {
    private final UserService userService;

    @PostMapping("/login")
    public Result<String> login(@RequestBody LoginData loginData) {
        User user = userService.login(loginData.getAccount(), loginData.getPassword());

        Map<String, Object> map = new HashMap<>();
        map.put("id", user.getId());
        map.put("account", user.getAccount());
        map.put("username", user.getUsername());

        String jwt = JwtUtil.generateJwt(map);
        return Result.success(jwt);
    }
    // 注册接口
    @PostMapping("/register")
    public Result<Integer> register(@Valid @RequestBody LoginData loginData) {
        int id = userService.register(loginData.getAccount(), loginData.getUsername(), loginData.getPassword());
        return Result.success(id);
    }
}
