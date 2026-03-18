package fun.medrec.spring.controller;

import fun.medrec.spring.domain.common.PageDTO;
import fun.medrec.spring.domain.common.PageVO;
import fun.medrec.spring.domain.common.Result;
import fun.medrec.spring.domain.entity.User;
import fun.medrec.spring.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@Slf4j
public class UserController {

    @Autowired
    private UserService userService;

    @RequestMapping("/list")
    public Result<PageVO<User>> getPage(@RequestBody PageDTO<User> page) {
        return Result.success(userService.getPage(page));
    }

    @RequestMapping("/{id}")
    public Result<User> getById(@PathVariable String id) {
        return Result.success(userService.getById(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Integer id) {
        return userService.removeById(id) ? Result.success() : Result.error("删除失败");
    }

    @PostMapping
    public Result<Void> add(@RequestBody User user) {
        return userService.save(user) ? Result.success() : Result.error("添加失败");
    }

    @PutMapping
    public Result<Void> update(@RequestBody User user) {
        return userService.updateById(user) ? Result.success() : Result.error("更新失败");
    }
}