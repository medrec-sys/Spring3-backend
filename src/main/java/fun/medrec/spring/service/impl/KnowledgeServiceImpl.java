package fun.medrec.spring.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.j256.simplemagic.ContentType;
import fun.medrec.spring.Ai.MyVectorStore;
import fun.medrec.spring.domain.bo.TextSegment;
import fun.medrec.spring.domain.common.PageDTO;
import fun.medrec.spring.domain.common.PageVO;
import fun.medrec.spring.domain.common.Result;
import fun.medrec.spring.domain.entity.Knowledge;
import fun.medrec.spring.domain.entity.Vector;
import fun.medrec.spring.exception.BusinessException;
import fun.medrec.spring.interceptor.UserContext;
import fun.medrec.spring.mapper.KnowledgeMapper;
import fun.medrec.spring.mapper.VectorMapper;
import fun.medrec.spring.service.HttpService;
import fun.medrec.spring.service.KnowledgeService;
import fun.medrec.spring.utils.MinioUtil;
import fun.medrec.spring.utils.TextUtil;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@Slf4j
public class KnowledgeServiceImpl extends ServiceImpl<KnowledgeMapper, Knowledge> implements KnowledgeService {
    final
    KnowledgeMapper knowledgeMapper;
    final
    VectorMapper vectorMapper;
    final
    HttpService httpService;

    public KnowledgeServiceImpl(KnowledgeMapper knowledgeMapper, HttpService httpService, VectorMapper vectorMapper) {
        this.knowledgeMapper = knowledgeMapper;
        this.httpService = httpService;
        this.vectorMapper = vectorMapper;
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

    @Override
    @SneakyThrows
    @Transactional
    public Integer save(MultipartFile multipartFile, Integer vectorId) {
        String contentType = multipartFile.getContentType();
        if (contentType == null || !contentType.equals(ContentType.PDF.getMimeType())) {
            throw new BusinessException("不支持文件格式:" + contentType);
        }
        String fileExtension = ContentType.fromMimeType(contentType).getFileExtensions()[0];
        MyVectorStore store = MyVectorStore.getStore(vectorId);
        if (store == null) {
            Vector vector = vectorMapper.selectById(vectorId);
            if (vector == null) {
                throw new BusinessException("向量库不存在,id:" + vectorId);
            }
            store = new MyVectorStore(vector);
        }

        int userId = UserContext.getId();

        String name = multipartFile.getOriginalFilename();
        String path = userId + "/" + vectorId + "/" +  System.currentTimeMillis() + "." + fileExtension;

        Result<List<TextSegment>> listResult = httpService.fileToMd(multipartFile);
        List<TextSegment> summarizer = TextUtil.summarizer(listResult.getData(), 1);

        Knowledge knowledge = new Knowledge();
        knowledge.setName(name);
        knowledge.setPath(path);
        knowledge.setVectorId(vectorId);
        knowledge.setChunk(summarizer.size());
        knowledge.setCreateBy(userId);
        knowledgeMapper.insert(knowledge);
        MinioUtil.loadFile(multipartFile, path);

        List<Document> documents = TextUtil.TextToDocuments(summarizer, knowledge.getId());
        store.addDocuments(documents);

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