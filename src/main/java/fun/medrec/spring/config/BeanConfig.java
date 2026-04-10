package fun.medrec.spring.config;

import fun.medrec.spring.utils.AsyncTaskUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class BeanConfig {

    @Bean
    public AsyncTaskUtil asyncTaskUtil() {
        return AsyncTaskUtil.getInstance();
    }
}
