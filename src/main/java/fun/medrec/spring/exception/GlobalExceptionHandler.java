package fun.medrec.spring.exception;

import fun.medrec.spring.domain.common.Result;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleException(Exception e) {
        return Result.error(e.getMessage());
    }
}
