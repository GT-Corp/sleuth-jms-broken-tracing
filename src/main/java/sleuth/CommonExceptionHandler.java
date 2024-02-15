package sleuth;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.filter.ServerHttpObservationFilter;



public class CommonExceptionHandler {
    
    Logger log = LoggerFactory.getLogger(CommonExceptionHandler.class);
    private final Tracer tracer;

    protected CommonExceptionHandler(Tracer tracer) {
        this.tracer = tracer;
    }

    @ExceptionHandler(BaseException.class)
    ProblemDetail onResourceNotFound(HttpServletRequest request, BaseException baseException) {
        ResponseStatus resp = getResponseStatus(baseException);
        return handleError(request, baseException, resp.code(), resp.reason());
    }

    static ResponseStatus getResponseStatus(BaseException ex) {
        /*
        Exception class must be annotated with @ResponseStatus
         */
        return ex.getClass().getAnnotationsByType(ResponseStatus.class)[0];
    }

    ProblemDetail handleError(HttpServletRequest request, Throwable error, HttpStatus status, String reason) {

        String requestURI = request.getRequestURI();
        StringBuffer requestURL = request.getRequestURL();
        if (requestURL != null) {
            requestURI = requestURL.toString();
        }
        String method = request.getMethod();
        String queryString = StringUtils.hasText(request.getQueryString()) ? "?" + request.getQueryString() : "";

        log.error("Failed to process {}: {}{}", method, requestURI, queryString, error);
        ServerHttpObservationFilter.findObservationContext(request).ifPresent(context -> context.setError(error));
        return createProblemDetail(status, reason, error);
    }

    ProblemDetail createProblemDetail(HttpStatus status, String reason, Throwable error) {
        var pd = ProblemDetail.forStatus(status);
        pd.setTitle(status.getReasonPhrase());
        pd.setDetail(error.toString());
        pd.setProperty("reason", reason);
        pd.setProperty("series", status.series());
        pd.setProperty("rootCause", ExceptionUtils.getRootCause(error).toString());
        pd.setProperty("trace", getTraceParent());

        return pd;
    }

    String getTraceParent() {
        Span span = tracer.currentSpan();
        if (span == null) {
            return "";
        }

        return "%s-%s".formatted(span.context().traceId(), span.context().spanId());
    }
}
