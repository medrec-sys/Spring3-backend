package fun.medrec.spring.controller;

import fun.medrec.spring.Ai.MyVectorStore;
import fun.medrec.spring.domain.common.PageDTO;
import fun.medrec.spring.domain.common.PageVO;
import fun.medrec.spring.domain.common.Result;
import fun.medrec.spring.domain.dto.VectorSearchArgs;
import fun.medrec.spring.domain.entity.Vector;
import fun.medrec.spring.service.VectorService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vector")
@Slf4j
public class VectorController {

    private final VectorService vectorService;

    public VectorController(VectorService vectorService) {
        this.vectorService = vectorService;
    }

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
    public Result<Integer> add(@Valid @RequestBody Vector vector) {
        return  Result.success(vectorService.create(vector));
    }

    @PutMapping
    public Result<Void> update(@Valid @RequestBody Vector vector) {
        vectorService.updateById(vector);
        vectorService.reBuild(vector);
        return  Result.success();
    }

    @PostMapping("/search/{id}")
    public Result<List<Document>> search(@PathVariable Integer id, @Valid @RequestBody VectorSearchArgs args) {
        MyVectorStore store = MyVectorStore.getStore(id);
        if (store == null) {
            store = vectorService.createVector(id);
        }
        return Result.success(vectorService.search(store, args));
    }

    @GetMapping("/agent/{id}")
    public Result<List<Vector>> getVectorByAgentId(@PathVariable Integer id) {
        return Result.success(vectorService.getVectorByAgentId(id));
    }
}