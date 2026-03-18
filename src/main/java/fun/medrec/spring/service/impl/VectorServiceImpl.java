package fun.medrec.spring.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import fun.medrec.spring.domain.common.PageDTO;
import fun.medrec.spring.domain.common.PageVO;
import fun.medrec.spring.domain.entity.Vector;
import fun.medrec.spring.mapper.VectorMapper;
import fun.medrec.spring.service.VectorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

@Service
public class VectorServiceImpl extends ServiceImpl<VectorMapper, Vector> implements VectorService {
    @Autowired
    private VectorMapper vectorMapper;

    @Override
    public PageVO<Vector> getPage(PageDTO<Vector> page) {
        Page<Vector> vectorPage = new Page<>(page.getPageNum(), page.getPageSize());

        LambdaQueryWrapper<Vector> queryWrapper = new LambdaQueryWrapper<>();

        Page<Vector> result = vectorMapper.selectPage(vectorPage, queryWrapper);
        return new PageVO<>(result.getTotal(), result.getRecords());
    }
}
