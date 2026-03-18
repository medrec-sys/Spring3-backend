package fun.medrec.spring.controller;

import fun.medrec.spring.domain.common.PageDTO;
import fun.medrec.spring.domain.common.PageVO;
import fun.medrec.spring.domain.common.Result;
import fun.medrec.spring.domain.entity.Vector;
import fun.medrec.spring.service.VectorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/vector")
@Slf4j
public class VectorController {

    @Autowired
    private VectorService vectorService;

    @RequestMapping("/list")
    public Result<PageVO<Vector>> getPage(@RequestBody PageDTO<Vector> page) {
        return Result.success(vectorService.getPage(page));
    }

    @RequestMapping("/{id}")
    public Result<Vector> getById(@PathVariable String id) {
        return Result.success(vectorService.getById(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Integer id) {
        return vectorService.removeById(id) ? Result.success() : Result.error("删除失败");
    }

    @PostMapping
    public Result<Void> add(@RequestBody Vector vector) {
        return vectorService.save(vector) ? Result.success() : Result.error("添加失败");
    }

    @PutMapping
    public Result<Void> update(@RequestBody Vector vector) {
        return vectorService.updateById(vector) ? Result.success() : Result.error("更新失败");
    }
}