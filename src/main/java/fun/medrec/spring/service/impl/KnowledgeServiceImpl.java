package fun.medrec.spring.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import fun.medrec.spring.domain.common.PageDTO;
import fun.medrec.spring.domain.common.PageVO;
import fun.medrec.spring.domain.entity.Knowledge;
import fun.medrec.spring.domain.entity.Vector;
import fun.medrec.spring.mapper.KnowledgeMapper;
import fun.medrec.spring.service.KnowledgeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeServiceImpl extends ServiceImpl<KnowledgeMapper, Knowledge> implements KnowledgeService {
    @Autowired
    KnowledgeMapper knowledgeMapper;

    @Override
    public PageVO<Knowledge> getPage(PageDTO<Knowledge> page) {
        Page<Knowledge> knowlePage = new Page<>(page.getPageNum(), page.getPageSize());

        LambdaQueryWrapper<Knowledge> queryWrapper = new LambdaQueryWrapper<>();

        Page<Knowledge> result = knowledgeMapper.selectPage(knowlePage, queryWrapper);
        return new PageVO<>(result.getTotal(), result.getRecords());
    }
}