package fun.medrec.spring.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import fun.medrec.spring.domain.common.PageDTO;
import fun.medrec.spring.domain.common.PageVO;
import fun.medrec.spring.domain.entity.User;
import fun.medrec.spring.exception.BusinessException;
import fun.medrec.spring.mapper.UserMapper;
import fun.medrec.spring.service.UserService;
import fun.medrec.spring.utils.BCryptUtil;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
    private final UserMapper userMapper;

    public UserServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public PageVO<User> getPage(PageDTO<User> page) {
        Page<User> userPage = new Page<>(page.getPageNum(), page.getPageSize());

        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();

        Page<User> result = userMapper.selectPage(userPage, queryWrapper);
        return new PageVO<>(result.getTotal(), result.getRecords());
    }

    @Override
    public User login(String account, String password) {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getAccount, account);
        User user = userMapper.selectOne(queryWrapper);
        if (user == null || !BCryptUtil.matches(password, user.getPassword())) {
            throw new BusinessException("账号或密码错误");
        }
        user.setLoginTime(new Date());
        userMapper.updateById(user);
        return user;
    }

    @Override
    public Integer register(String account, String username, String password) {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getAccount, account);
        if (this.count(queryWrapper) > 0) {
            throw  new BusinessException("用户已存在");
        }

        User user = new User();
        user.setAccount(account);
        user.setUsername(username);
        user.setPassword(BCryptUtil.encode(password));
        this.save(user);
        return user.getId();
    }

    @Override
    public Integer create(User user) {
        user.setId(null);
        user.setPassword(BCryptUtil.encode(user.getPassword()));
        return userMapper.insert(user);
    }
}
