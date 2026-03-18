package fun.medrec.spring.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import fun.medrec.spring.domain.entity.User;
import org.apache.ibatis.annotations.Mapper;


@Mapper
public interface UserMapper extends BaseMapper<User> {
}