package fun.medrec.spring.utils;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import fun.medrec.spring.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

@Slf4j
public class AsyncTaskUtil {
    private static final long EXPIRE_MINUTES = 60;

    private static final AsyncTaskUtil INSTANCE = new AsyncTaskUtil();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Task {
        private String description;
        private String status;
        private int code;

        public Task(String description) {
            this.description = description;
            this.status = "等待处理";
            this.code = 0;
            log.info("创建任务：{}--{}", description, this);
        }
    }

    private final Cache<String, Task> cache;

    private AsyncTaskUtil() {
        cache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterAccess(EXPIRE_MINUTES, TimeUnit.MINUTES)
                .removalListener((key, value, cause) -> log.info("任务已删除: key: {}, 任务描述: {}, 原因: {}", key,
                        value instanceof Task ? ((Task) value).getDescription() : "null", cause))
                .build();
    }

    public static AsyncTaskUtil getInstance() {
        return INSTANCE;
    }

    public Task getTask(String taskId) {
        return cache.getIfPresent(taskId);
    }

    public void putTask(String taskId, Task task) {
        cache.put(taskId, task);
    }

    private void updateTask(String taskId, String status, int code) {
        Task task = getTask(taskId);
        if (task != null) {
            task.status = status;
            task.code = code;
            putTask(taskId, task);
            log.info("更新任务：{}--{}", taskId, task);
        } else {
            throw new BusinessException("任务不存在");
        }
    }

    public void updateTask(String taskId, String status) {
        updateTask(taskId, status, 0);

    }

    public void finishTask(String taskId) {
        updateTask(taskId, "任务完成", 1);
    }

    public void errorTask(String taskId, String errorMsg) {
        updateTask(taskId, "任务失败:" + errorMsg, -1);
    }
}
