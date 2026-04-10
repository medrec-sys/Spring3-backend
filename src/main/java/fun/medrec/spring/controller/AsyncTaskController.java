package fun.medrec.spring.controller;

import fun.medrec.spring.domain.common.Result;
import fun.medrec.spring.utils.AsyncTaskUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/task")
@Slf4j
public class AsyncTaskController {
    private final AsyncTaskUtil asyncTaskUtil;

    public AsyncTaskController(AsyncTaskUtil asyncTaskUtil) {
        this.asyncTaskUtil = asyncTaskUtil;
    }

    @GetMapping
    public Result<AsyncTaskUtil.Task> getTask(String taskId) {
        return Result.success(asyncTaskUtil.getTask(taskId));
    }
}
