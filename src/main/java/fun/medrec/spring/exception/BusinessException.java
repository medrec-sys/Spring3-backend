package fun.medrec.spring.exception;

public class BusinessException extends RuntimeException{
    public BusinessException(String err) {
        super(err);
    }
}
