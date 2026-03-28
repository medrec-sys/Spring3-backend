package fun.medrec.spring.controller;

import fun.medrec.spring.domain.common.PageDTO;
import fun.medrec.spring.domain.common.PageVO;
import fun.medrec.spring.domain.common.Result;
import fun.medrec.spring.domain.entity.Knowledge;
import fun.medrec.spring.service.KnowledgeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/knowledge")
@Slf4j
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @PostMapping("/list")
    public Result<PageVO<Knowledge>> getPage(@RequestBody PageDTO<Knowledge> page) {
        return Result.success(knowledgeService.getPage(page));
    }

    @GetMapping("/{id}")
    public Result<Knowledge> getById(@PathVariable String id) {

        return Result.success(knowledgeService.getById(id)
        );
    }

    @DeleteMapping("/{id}")
    public Result<Integer> delete(@PathVariable Integer id) {
        return Result.success(knowledgeService.delete(id));
    }

    @PostMapping("/{vectorId}")
    public Result<Integer> add(@RequestParam MultipartFile file, @PathVariable Integer vectorId) {
        return  Result.success(knowledgeService.save(file, vectorId));
    }

    @PutMapping
    public Result<Void> update(@RequestBody Knowledge knowledge) {
        return knowledgeService.updateById(knowledge) ? Result.success() : Result.error("更新失败");
    }
}