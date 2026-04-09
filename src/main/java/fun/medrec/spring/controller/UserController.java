package fun.medrec.spring.controller;

import fun.medrec.spring.domain.common.PageDTO;
import fun.medrec.spring.domain.common.PageVO;
import fun.medrec.spring.domain.common.Result;
import fun.medrec.spring.domain.entity.User;
import fun.medrec.spring.service.UserService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@Slf4j
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/list")
    public Result<PageVO<User>> getPage(@RequestBody PageDTO<User> page) {
        return Result.success(userService.getPage(page));
    }

    @GetMapping("/{id}")
    public Result<User> getById(@PathVariable String id) {
        return Result.success(userService.getById(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Integer id) {
        return userService.removeById(id) ? Result.success() : Result.error("删除失败");
    }

    @PostMapping
    public Result<Integer> add(@Valid @RequestBody User user) {
        return Result.success(userService.create(user));
    }

    @PutMapping
    public Result<Void> update(@Valid @RequestBody User user) {
        return userService.updateById(user) ? Result.success() : Result.error("更新失败");
    }
}