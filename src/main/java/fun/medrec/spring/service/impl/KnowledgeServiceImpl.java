package fun.medrec.spring.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.j256.simplemagic.ContentType;
import fun.medrec.spring.Ai.MyVectorStore;
import fun.medrec.spring.domain.bo.TextSegment;
import fun.medrec.spring.domain.common.PageDTO;
import fun.medrec.spring.domain.common.PageVO;
import fun.medrec.spring.domain.entity.Knowledge;
import fun.medrec.spring.domain.entity.Vector;
import fun.medrec.spring.exception.BusinessException;
import fun.medrec.spring.mapper.KnowledgeMapper;
import fun.medrec.spring.mapper.VectorMapper;
import fun.medrec.spring.service.KnowledgeService;
import fun.medrec.spring.utils.AsyncTaskUtil;
import fun.medrec.spring.utils.MinerUtil;
import fun.medrec.spring.utils.MinioUtil;
import fun.medrec.spring.utils.TextUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;

import static java.lang.Thread.sleep;

@Service
@Slf4j
public class KnowledgeServiceImpl extends ServiceImpl<KnowledgeMapper, Knowledge> implements KnowledgeService {
    final KnowledgeMapper knowledgeMapper;
    final VectorMapper vectorMapper;
    final AsyncTaskUtil asyncTaskUtil;
    @Lazy
    @Autowired
    private KnowledgeService self;
    final TextUtil textUtil;
    final MinerUtil minerUtil;

    // 全局锁，确保同一时间只有一个异步任务执行
    private final Semaphore semaphore = new Semaphore(1);


    public KnowledgeServiceImpl(KnowledgeMapper knowledgeMapper, VectorMapper vectorMapper, AsyncTaskUtil asyncTaskUtil, TextUtil textUtil, MinerUtil minerUtil) {
        this.knowledgeMapper = knowledgeMapper;
        this.vectorMapper = vectorMapper;
        this.asyncTaskUtil = asyncTaskUtil;
        this.textUtil = textUtil;
        this.minerUtil = minerUtil;
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
    public CompletableFuture<Integer> saveAsync(String taskId, MultipartFile file, Integer vectorId, Integer userId) {
        Integer knowledgeId = null;
        MyVectorStore store = null;
        // 同步校验（必须在异步外校验，失败能立刻返回）
        try {
            String contentType = file.getContentType();
            if (contentType == null || !contentType.equals(ContentType.PDF.getMimeType())) {
                throw new BusinessException("不支持文件格式:" + contentType);
            }
            String fileExtension = ContentType.fromMimeType(contentType).getFileExtensions()[0];

            // 检查向量库
            Vector vector = vectorMapper.selectById(vectorId);
            if (vector == null) {
                throw new BusinessException("向量库不存在,id:" + vectorId);
            }

            String name = file.getOriginalFilename();
            String path = userId + "/" + vectorId + "/" + System.currentTimeMillis() + "." + fileExtension;

            AsyncTaskUtil.Task task = new AsyncTaskUtil.Task("添加知识文件：" + name);
            asyncTaskUtil.putTask(taskId, task);

            asyncTaskUtil.updateTask(taskId, "等待执行许可");
            semaphore.acquire();

            List<MinerUtil.ContentItem> contentItems = List.of();
            try {
                asyncTaskUtil.updateTask(taskId, "文件转md");
                String s = minerUtil.uploadAndParse(List.of(file));
                while (true) {
                    MinerUtil.PollingResult pollingResult = minerUtil.getZipUrl(s);
                    sleep(1000);
                    if (pollingResult.isSuccess()) {
                        List<String> zipUrls = pollingResult.getZipUrls();
                        for (String url : zipUrls) {
                            contentItems = minerUtil.handleParseResult(url);
                        }
                        break;
                    } else {
                        asyncTaskUtil.updateTask(taskId, pollingResult.getInfo());
                    }
                }

                asyncTaskUtil.updateTask(taskId, "保存知识元数据");
                knowledgeId = self.saveKnowledgeWithTransaction(name, path, vectorId, -1, userId);

                asyncTaskUtil.updateTask(taskId, "文本格式转换");
                List<TextSegment> textSegments = textUtil.textSegmentsFromMiner(contentItems, knowledgeId);

                asyncTaskUtil.updateTask(taskId, "总结知识片段");
                List<TextSegment> summarizer = textUtil.strengthenWithSpilt(textSegments);

               // 保存知识块数量
                LambdaUpdateWrapper<Knowledge> wrapper = new LambdaUpdateWrapper<>();
                wrapper.eq(Knowledge::getId, knowledgeId);
                wrapper.set(Knowledge::getChunk, summarizer.size());
                self.update(wrapper);

                // 4. 文件上传（无事务）
                asyncTaskUtil.updateTask(taskId, "保存文件");
                MinioUtil.loadFile(file, path);

                // 5. 向量库写入
                asyncTaskUtil.updateTask(taskId, "写入向量库");
                store = new MyVectorStore(vector);
                List<Document> documents = textUtil.toDocsWithSplit(summarizer);
                store.addDocuments(documents);

                asyncTaskUtil.finishTask(taskId);
                log.info("异步任务[{}]执行完成，knowledgeId:{}", taskId, knowledgeId);
                return CompletableFuture.completedFuture(knowledgeId);
            } finally {
                semaphore.release();
                log.info("信号量释放成功，taskId:{}", taskId);
            }
        } catch (Exception e) {
            asyncTaskUtil.errorTask(taskId, "失败：" + e.getMessage());
            log.error("异步任务[{}]执行失败", taskId, e);
            // 回调 删除数据库数据和minio文件和redis文本
            if (knowledgeId != null) {
                Knowledge knowledge = baseMapper.selectById(knowledgeId);
                baseMapper.deleteById(knowledgeId);

                MinioUtil.deleteFile(knowledge.getPath());
                if (store != null) {
                    store.delete(knowledgeId, knowledge.getChunk() == null ? 100 : knowledge.getChunk());
                }

            }
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