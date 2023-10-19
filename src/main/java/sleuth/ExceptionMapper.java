package sleuth;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
@Slf4j
public class ExceptionMapper extends ResponseEntityExceptionHandler {

    final HttpServletRequest httpRequest; //binds current request

    ExceptionMapper(HttpServletRequest httpRequest) {
        this.httpRequest = httpRequest;
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception t, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        log.error("Exception thrown from " + httpRequest.getMethod() + ":" + httpRequest.getRequestURI(), t);
        return super.handleExceptionInternal(t, body, headers, status, request);
    }


    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    static class CustomException extends RuntimeException {

    }
}

@RestController
class Ctrl2 {

    @GetMapping("/exception")
    void x() {
        throw new ExceptionMapper.CustomException();
    }
}

