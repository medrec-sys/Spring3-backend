package fun.medrec.spring.controller;


import fun.medrec.spring.Ai.MyVectorStore;
import fun.medrec.spring.domain.bo.ReusableMultipartFile;
import fun.medrec.spring.domain.common.PageDTO;
import fun.medrec.spring.domain.common.PageVO;
import fun.medrec.spring.domain.common.Result;
import fun.medrec.spring.domain.entity.Knowledge;
import fun.medrec.spring.domain.entity.Vector;
import fun.medrec.spring.interceptor.UserContext;
import fun.medrec.spring.service.KnowledgeService;
import fun.medrec.spring.service.VectorService;
import fun.medrec.spring.utils.AsyncTaskUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@RestController
@RequestMapping("/api/knowledge")
@Slf4j
public class KnowledgeController {

    private final KnowledgeService knowledgeService;
    private final VectorService vectorService;
    private final AsyncTaskUtil asyncTaskUtil;

    public KnowledgeController(KnowledgeService knowledgeService, VectorService vectorService, AsyncTaskUtil asyncTaskUtil) {
        this.knowledgeService = knowledgeService;
        this.vectorService = vectorService;
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

    @PostMapping("/docs")
    public Result<List<Document>> getDocsByIds(@RequestBody List<String> ids) {
        log.info("获取知识文档 id: {}", ids);
        List<Integer> knowledgeIds = ids.stream().map(s -> Integer.valueOf(s.split("_")[0])).toList();
        List<Knowledge> knowledges = knowledgeService.getByIds(knowledgeIds);

        Map<Integer, String> bookMap = new HashMap<>();
        Map<String, Integer> map = new HashMap<>();

        for (Knowledge knowledge : knowledges) {
            bookMap.put(knowledge.getId(), knowledge.getName());
        }
        for (Knowledge knowledge : knowledges) {
            map.put(knowledge.getPath(), knowledge.getVectorId());
        }
        List<Document> documents = new ArrayList<>();
        for (String path : map.keySet()) {
            Integer vectorId = map.get(path);
            MyVectorStore store = MyVectorStore.getStore(vectorId);
            if (store == null) {
                Vector vector = vectorService.getById(vectorId);
                store = vectorService.reBuild(vector);
            }
            List<Document> docByIds = store.getDocByIds(ids);
            for (Document document : docByIds) {
                document.getMetadata().put("url", path);
            }
            documents.addAll(docByIds);
        }
        documents.forEach(((doc) -> {
            doc.getMetadata().put("name", bookMap.get( Integer.parseInt((String) doc.getMetadata().get("bookId"))));
        }));


        return Result.success(documents);
    }
}