package fun.medrec.spring.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import fun.medrec.spring.domain.common.PageDTO;
import fun.medrec.spring.domain.common.PageVO;
import fun.medrec.spring.domain.entity.Agent;
import fun.medrec.spring.mapper.AgentMapper;
import fun.medrec.spring.service.AgentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AgentServiceImpl extends ServiceImpl<AgentMapper, Agent> implements AgentService {
    @Autowired
    private AgentMapper agentMapper;

    @Override
    public PageVO<Agent> getPage(PageDTO<Agent> page) {
        Page<Agent> agentPage = new Page<>(page.getPageNum(), page.getPageSize());

        LambdaQueryWrapper<Agent> queryWrapper = new LambdaQueryWrapper<>();

        Page<Agent> result = agentMapper.selectPage(agentPage, queryWrapper);
        return new PageVO<>(result.getTotal(), result.getRecords());
    }
}
