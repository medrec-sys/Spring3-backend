package fun.medrec.spring.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.j256.simplemagic.ContentType;
import fun.medrec.spring.domain.Ai.MyVectorStore;
import fun.medrec.spring.domain.common.PageDTO;
import fun.medrec.spring.domain.common.PageVO;
import fun.medrec.spring.domain.entity.Knowledge;
import fun.medrec.spring.domain.entity.Vector;
import fun.medrec.spring.exception.BusinessException;
import fun.medrec.spring.interceptor.UserContext;
import fun.medrec.spring.mapper.KnowledgeMapper;
import fun.medrec.spring.mapper.VectorMapper;
import fun.medrec.spring.service.KnowledgeService;
import fun.medrec.spring.service.VectorService;
import fun.medrec.spring.utils.MinioUtil;
import fun.medrec.spring.utils.TextUtil;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@Slf4j
public class KnowledgeServiceImpl extends ServiceImpl<KnowledgeMapper, Knowledge> implements KnowledgeService {
    @Autowired
    KnowledgeMapper knowledgeMapper;
    @Autowired
    VectorMapper vectorMapper;

    @Override
    public PageVO<Knowledge> getPage(PageDTO<Knowledge> page) {
        Page<Knowledge> knowlePage = new Page<>(page.getPageNum(), page.getPageSize());

        LambdaQueryWrapper<Knowledge> queryWrapper = new LambdaQueryWrapper<>();

        Page<Knowledge> result = knowledgeMapper.selectPage(knowlePage, queryWrapper);
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
        String path = MinioUtil.getFileUrl(userId + "/" + vectorId + "/" +  System.currentTimeMillis() + "." + contentType);

        Knowledge knowledge = new Knowledge();
        knowledge.setName(name);
        knowledge.setPath(path);
        knowledge.setVectorId(vectorId);
        knowledge.setCreateBy(userId);
        knowledgeMapper.insert(knowledge);
        MinioUtil.loadFile(multipartFile, path);
        TextUtil.TextData textData = TextUtil.readPdf(multipartFile.getInputStream(), name);
        textData.setId(knowledge.getId());


        TextUtil.TextData textData1 = store.mergeSentence(textData);
        store.addDocuments(TextUtil.TextToDocument(textData1));


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
            Vector vector = vectorMapper.selectById(id);
            store = new MyVectorStore(vector);
        }
        store.delete(id, knowledge.getVectorId());
        MinioUtil.deleteFile(knowledge.getPath());
        return id;
    }

    @Override
    @Transactional
    public void deleteByVectorId(Integer id) {
        LambdaQueryWrapper<Knowledge> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Knowledge::getVectorId, id);
        List<Knowledge> knowledges = knowledgeMapper.selectList(queryWrapper);
        knowledgeMapper.delete(queryWrapper);
        MyVectorStore store = MyVectorStore.getStore(knowledges.getFirst().getVectorId());
        if (store == null) {
            Vector vector = vectorMapper.selectById(id);
            store = new MyVectorStore(vector);
        }
        for (Knowledge knowledge : knowledges) {
            store.delete(id, knowledge.getVectorId());
            MinioUtil.deleteFile(knowledge.getPath());
        }
    }
}