package fun.medrec.spring.controller;

import fun.medrec.spring.domain.common.PageDTO;
import fun.medrec.spring.domain.common.PageVO;
import fun.medrec.spring.domain.common.Result;
import fun.medrec.spring.domain.entity.Knowledge;
import fun.medrec.spring.service.KnowledgeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/knowledge")
@Slf4j
public class KnowledgeController {

    @Autowired
    private KnowledgeService knowledgeService;

    @RequestMapping("/list")
    public Result<PageVO<Knowledge>> getPage(@RequestBody PageDTO<Knowledge> page) {
        return Result.success(knowledgeService.getPage(page));
    }

    @RequestMapping("/{id}")
    public Result<Knowledge> getById(@PathVariable String id) {
        return Result.success(knowledgeService.getById(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Integer id) {
        return knowledgeService.removeById(id) ? Result.success() : Result.error("删除失败");
    }

    @PostMapping
    public Result<Void> add(@RequestBody Knowledge knowledge) {
        return knowledgeService.save(knowledge) ? Result.success() : Result.error("添加失败");
    }

    @PutMapping
    public Result<Void> update(@RequestBody Knowledge knowledge) {
        return knowledgeService.updateById(knowledge) ? Result.success() : Result.error("更新失败");
    }
}