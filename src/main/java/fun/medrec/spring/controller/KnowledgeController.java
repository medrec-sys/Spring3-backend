package fun.medrec.spring.controller;


import fun.medrec.spring.domain.bo.ReusableMultipartFile;
import fun.medrec.spring.domain.common.PageDTO;
import fun.medrec.spring.domain.common.PageVO;
import fun.medrec.spring.domain.common.Result;
import fun.medrec.spring.domain.entity.Knowledge;
import fun.medrec.spring.interceptor.UserContext;
import fun.medrec.spring.service.KnowledgeService;
import fun.medrec.spring.utils.AsyncTaskUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/knowledge")
@Slf4j
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    private final AsyncTaskUtil asyncTaskUtil;

    public KnowledgeController(KnowledgeService knowledgeService, AsyncTaskUtil asyncTaskUtil) {
        this.knowledgeService = knowledgeService;
        this.asyncTaskUtil = asyncTaskUtil;
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
        log.info("删除知识 id: {}", id);
        return Result.success(knowledgeService.delete(id));
    }

    @PostMapping("/{vectorId}")
    public Result<String> add(@RequestParam MultipartFile file, @PathVariable Integer vectorId) {
        log.info("添加知识\n 文件：{}\n大小: {}", file.getOriginalFilename(), file.getSize());
        String taskId = UUID.randomUUID().toString();

        int userId = UserContext.getId();
        ReusableMultipartFile reusableMultipartFile;
        try {
            reusableMultipartFile = new ReusableMultipartFile(file);
        } catch (Exception e) {
            log.error("文件上传失败", e);
            throw new RuntimeException("文件上传失败");
        }

        knowledgeService.saveAsync(taskId, reusableMultipartFile, vectorId, userId)
                .thenAccept(result -> asyncTaskUtil.finishTask(taskId))
                .exceptionally(throwable -> {
                    asyncTaskUtil.errorTask(taskId, throwable.toString());
                    return null;
                });

        // 立即返回任务ID
        return Result.success(taskId);
    }

    @PutMapping
    public Result<Void> update(@RequestBody Knowledge knowledge) {
        return knowledgeService.updateById(knowledge) ? Result.success() : Result.error("更新失败");
    }

    @GetMapping("/vector/{id}")
    public Result<List<Knowledge>> getByVectorId(@PathVariable Integer id) {
        return Result.success(knowledgeService.getByVectorId(id));
    }
}