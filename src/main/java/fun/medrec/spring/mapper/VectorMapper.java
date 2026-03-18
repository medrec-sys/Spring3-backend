package fun.medrec.spring.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import fun.medrec.spring.domain.entity.Vector;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface VectorMapper extends BaseMapper<Vector> {
}
