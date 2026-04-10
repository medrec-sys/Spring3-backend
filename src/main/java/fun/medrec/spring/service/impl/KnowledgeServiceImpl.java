package fun.medrec.spring.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.j256.simplemagic.ContentType;
import fun.medrec.spring.Ai.MyVectorStore;
import fun.medrec.spring.domain.bo.FileData;
import fun.medrec.spring.domain.bo.TextSegment;
import fun.medrec.spring.domain.common.PageDTO;
import fun.medrec.spring.domain.common.PageVO;
import fun.medrec.spring.domain.common.Result;
import fun.medrec.spring.domain.entity.Knowledge;
import fun.medrec.spring.domain.entity.Vector;
import fun.medrec.spring.exception.BusinessException;
import fun.medrec.spring.mapper.KnowledgeMapper;
import fun.medrec.spring.mapper.VectorMapper;
import fun.medrec.spring.service.HttpService;
import fun.medrec.spring.service.KnowledgeService;
import fun.medrec.spring.utils.AsyncTaskUtil;
import fun.medrec.spring.utils.MinioUtil;
import fun.medrec.spring.utils.TextUtil;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;

@Service
@Slf4j
public class KnowledgeServiceImpl extends ServiceImpl<KnowledgeMapper, Knowledge> implements KnowledgeService {
    final
    KnowledgeMapper knowledgeMapper;
    final
    VectorMapper vectorMapper;
    final
    HttpService httpService;
    final
    AsyncTaskUtil asyncTaskUtil;
    @Lazy
    @Autowired
    private KnowledgeService self;

    // 全局锁，确保同一时间只有一个异步任务执行
    private final Semaphore semaphore = new Semaphore(1);


    public KnowledgeServiceImpl(KnowledgeMapper knowledgeMapper, HttpService httpService, VectorMapper vectorMapper, AsyncTaskUtil asyncTaskUtil) {
        this.knowledgeMapper = knowledgeMapper;
        this.httpService = httpService;
        this.vectorMapper = vectorMapper;
        this.asyncTaskUtil = asyncTaskUtil;
    }

    @Override
    public PageVO<Knowledge> getPage(PageDTO<Knowledge> page) {
        Page<Knowledge> knowlePage = new Page<>(page.getPageNum(), page.getPageSize());

        LambdaQueryWrapper<Knowledge> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Knowledge::getVectorId, page.getQuery().getVectorId());
        if (page.getQuery().getName() != null && !page.getQuery().getName().isEmpty()) {
            queryWrapper.like(Knowledge::getName, page.getQuery().getName());
        }

        Page<Knowledge> result = knowledgeMapper.selectPage(knowlePage, queryWrapper);
        result.getRecords().forEach(knowledge -> knowledge.setPath(MinioUtil.getFileUrl(knowledge.getPath())));
        return new PageVO<>(result.getTotal(), result.getRecords());
    }

    @Async
    @Override
    public CompletableFuture<Integer> saveAsync(String taskId, FileData fileData, Integer vectorId, Integer userId) {
        // 1. 同步校验（必须在异步外校验，失败能立刻返回）
        try {
            String contentType = fileData.getContentType();
            if (contentType == null || !contentType.equals(ContentType.PDF.getMimeType())) {
                throw new BusinessException("不支持文件格式:" + contentType);
            }
            String fileExtension = ContentType.fromMimeType(contentType).getFileExtensions()[0];

            // 检查向量库
            Vector vector = vectorMapper.selectById(vectorId);
            if (vector == null) {
                throw new BusinessException("向量库不存在,id:" + vectorId);
            }

            String name = fileData.getName();
            String path = userId + "/" + vectorId + "/" + System.currentTimeMillis() + "." + fileExtension;

            AsyncTaskUtil.Task task = new AsyncTaskUtil.Task("添加知识文件：" + name);
            asyncTaskUtil.putTask(taskId, task);

            asyncTaskUtil.updateTask(taskId, "等待执行许可");
            semaphore.acquire();

            try {
                // 2. 执行耗时操作（无事务）
                asyncTaskUtil.updateTask(taskId, "文件转md（需要较长时间）");
                Result<List<TextSegment>> listResult = httpService.fileToMd(fileData);
                asyncTaskUtil.updateTask(taskId, "总结知识片段");
                List<TextSegment> summarizer = TextUtil.summarizer(listResult.getData(), 2);

                // 3. 【核心】数据库操作单独事务方法（保证原子性）
                asyncTaskUtil.updateTask(taskId, "保存知识元数据");
                Integer knowledgeId = self.saveKnowledgeWithTransaction(name, path, vectorId, summarizer.size(), userId);

                // 4. 文件上传（无事务）
                MinioUtil.loadFile(fileData, path);

                // 5. 向量库写入
                asyncTaskUtil.updateTask(taskId, "写入向量库");
                MyVectorStore store = new MyVectorStore(vector);
                List<Document> documents = TextUtil.TextToDocuments(summarizer, knowledgeId);
                store.addDocuments(documents);

                asyncTaskUtil.finishTask(taskId);
                log.info("异步任务[{}]执行完成，knowledgeId:{}", taskId, knowledgeId);
                return CompletableFuture.completedFuture(knowledgeId);
            } finally {
                semaphore.release();
                log.info("信号量释放成功，taskId:{}", taskId);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            asyncTaskUtil.errorTask(taskId, "任务被中断");
            log.error("异步任务[{}]被中断", taskId, e);
            return CompletableFuture.failedFuture(e);
        } catch (Exception e) {
            asyncTaskUtil.errorTask(taskId, "失败：" + e.getMessage());
            log.error("异步任务[{}]执行失败", taskId, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * 【关键】独立事务方法，保证原子性
     * REQUIRES_NEW：独立事务，不受异步影响
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Integer saveKnowledgeWithTransaction(String name, String path, Integer vectorId,
                                                Integer chunk, Integer userId) {
        Knowledge knowledge = new Knowledge();
        knowledge.setName(name);
        knowledge.setPath(path);
        knowledge.setVectorId(vectorId);
        knowledge.setChunk(chunk);
        knowledge.setCreateBy(userId);
        knowledgeMapper.insert(knowledge);
        return knowledge.getId();
    }

    @Override
    @Transactional
    public Integer delete(Integer id) {
        Knowledge knowledge = knowledgeMapper.selectById(id);
        if (knowledge == null) {
            throw new BusinessException("知识不存在,id:" + id);
        }
        knowledgeMapper.deleteById(id);
        MyVectorStore store = MyVectorStore.getStore(knowledge.getVectorId());
        if (store == null) {
            Vector vector = vectorMapper.selectById(knowledge.getVectorId());
            store = new MyVectorStore(vector);
        }
        store.delete(id, knowledge.getChunk());
        MinioUtil.deleteFile(knowledge.getPath());
        return id;
    }

    @Override
    public Knowledge getById(Integer id) {
        Knowledge byId = super.getById(id);
        byId.setPath(MinioUtil.getFileUrl(byId.getPath()));
        return byId;
    }

    @Override
    public List<Knowledge> getByIds(List<Integer> ids) {
        List<Knowledge> knowledges = knowledgeMapper.selectByIds(ids);
        knowledges.forEach(knowledge -> knowledge.setPath(MinioUtil.getFileUrl(knowledge.getPath())));
        return knowledges;
    }


    @Override
    @Transactional
    public void deleteByVectorId(Integer id) {
        LambdaQueryWrapper<Knowledge> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Knowledge::getVectorId, id);
        List<Knowledge> knowledges = knowledgeMapper.selectList(queryWrapper);
        if (knowledges.isEmpty()) {
            return;
        }
        knowledgeMapper.delete(queryWrapper);
        MyVectorStore store = MyVectorStore.getStore(knowledges.getFirst().getVectorId());
        if (store == null) {
            Vector vector = vectorMapper.selectById(id);
            store = new MyVectorStore(vector);
        }
        for (Knowledge knowledge : knowledges) {
            store.delete(knowledge.getId(), knowledge.getChunk());
            MinioUtil.deleteFile(knowledge.getPath());
        }
    }

    @Override
    public List<Knowledge> getByVectorId(Integer id) {
        LambdaQueryWrapper<Knowledge> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Knowledge::getVectorId, id);
        List<Knowledge> knowledges = knowledgeMapper.selectList(queryWrapper);
        knowledges.forEach(knowledge -> knowledge.setPath(MinioUtil.getFileUrl(knowledge.getPath())));
        return knowledges;
    }
}