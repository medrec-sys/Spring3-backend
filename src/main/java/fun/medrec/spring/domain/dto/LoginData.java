package fun.medrec.spring.domain.dto;

import lombok.Data;

@Data
public class LoginData {
    private String account;
    private String username;
    private String password;
}
