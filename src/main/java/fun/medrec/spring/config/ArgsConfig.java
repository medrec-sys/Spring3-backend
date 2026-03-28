package fun.medrec.spring.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ArgsConfig {
    @Value("${webclient.fastApi}")
    public String fastApiUrl;
}
