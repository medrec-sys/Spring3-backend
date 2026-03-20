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

    @PostMapping("/list")
    public Result<PageVO<Vector>> getPage(@RequestBody PageDTO<Vector> page) {
        return Result.success(vectorService.getPage(page));
    }

    @GetMapping("/{id}")
    public Result<Vector> getById(@PathVariable String id) {
        return Result.success(vectorService.getById(id));
    }

    @DeleteMapping("/{id}")
    public Result<Integer> delete(@PathVariable Integer id) {
        return Result.success(vectorService.delete(id));
    }

    @PostMapping
    public Result<Integer> add(@RequestBody Vector vector) {
        return  Result.success(vectorService.create(vector));
    }

    @PutMapping
    public Result<Void> update(@RequestBody Vector vector) {
        vectorService.updateById(vector);
        vectorService.reBuild(vector);
        return  Result.success();
    }
}