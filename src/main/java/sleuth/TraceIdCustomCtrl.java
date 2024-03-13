package sleuth;

import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/*
demonstrates how the trace id generation works
 */
@RestController
public class TraceIdCustomCtrl {
    static Logger log = LoggerFactory.getLogger(TraceIdCustomCtrl.class);

    @Autowired
    Tracer tracer;

    @GetMapping("/custom-trace")
    void accept() {
        String orgTraceId = tracer.currentTraceContext().context().traceId();
        String orgSpanId = tracer.currentTraceContext().context().spanId();
        log.info("This is how we get current trace id {}", orgTraceId);
        log.info("This is how we get current span id {}", orgSpanId);

        //test 1
        tracer.startScopedSpan("new span1");
        log.info("Only the span id changes after startScopedSpan. original span will not be used anymore.. unless we manually set it");


        //test 2
        //set new trace id
        TraceContext tc = tracer.traceContextBuilder()
                .traceId("4bf92f3577b34da6a3ce929d0e0e4736")
                .spanId("00f067aa0ba902b7")
                .sampled(false)
                .build();

        try (var sc = tracer.currentTraceContext().newScope(tc)) {
            log.info("Now using custom trace id and span id log ");
        }

        //test3
        log.info("we will have original trace id here... ");


        //test4
        //set another trace id
        tc = tracer.traceContextBuilder()
                .traceId("4bf92f3577b34da6a3ce929d0e0e0000")
                .spanId("00f067aa0ba902b0")
                .sampled(false)
                .build();

        try (var sc = tracer.currentTraceContext().newScope(tc)) {
            log.info("Now using custom trace id and span id log ");

            //test5 - setting original trace id
            tc = tracer.traceContextBuilder()
                    .traceId(orgTraceId)
                    .spanId(orgTraceId)
                    .sampled(false)
                    .build();
            try (var sc2 = tracer.currentTraceContext().newScope(tc)) {
                log.info("Now using ORIGINAL custom trace id and span id log ");
            }
        }

        //test 6
        log.info("we will have original trace id here... ");


    }
}
