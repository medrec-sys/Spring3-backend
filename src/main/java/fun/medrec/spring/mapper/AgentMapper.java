package fun.medrec.spring.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import fun.medrec.spring.domain.entity.Agent;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AgentMapper extends BaseMapper<Agent> {
}