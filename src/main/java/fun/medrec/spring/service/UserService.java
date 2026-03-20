package fun.medrec.spring.service;

import com.baomidou.mybatisplus.extension.service.IService;
import fun.medrec.spring.domain.common.PageDTO;
import fun.medrec.spring.domain.common.PageVO;
import fun.medrec.spring.domain.entity.User;

public interface UserService extends IService<User> {
    PageVO<User> getPage(PageDTO<User> page);

    User login(String account, String password);

    Integer register(String account, String username, String password);

    Integer create(User user);
}