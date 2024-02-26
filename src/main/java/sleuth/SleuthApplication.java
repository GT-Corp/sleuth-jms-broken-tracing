package sleuth;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.propagation.ThreadLocalSpan;
import io.micrometer.observation.ObservationRegistry;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import jakarta.jms.TextMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jms.DefaultJmsListenerContainerFactoryConfigurer;
import org.springframework.boot.task.ThreadPoolTaskExecutorBuilder;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignClientsConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskDecorator;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.ErrorHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import sleuth.feign.FeignTestClient;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableJms
public class SleuthApplication {

    static Logger log = LoggerFactory.getLogger(SleuthApplication.class);

    public static void main(String[] args) {
        new SpringApplication(SleuthApplication.class).run(args);
    }


    @Configuration
    @EnableFeignClients(basePackages = "sleuth")
    @Import(FeignClientsConfiguration.class)
    static class FeignConfiguration {

        @Bean
        feign.Logger.Level feignLoggerLevel() {
            return feign.Logger.Level.FULL;
        }
    }

    @Configuration
    static class ConfigXXX {

        @Bean
        RestTemplate restTemplate(RestTemplateBuilder rb) {
            return rb.build();
        }

        @Bean
        public TaskDecorator decorator() {
            return new ContextPropagatingTaskDecorator();
        }

        @Bean
        @Primary
        AsyncTaskExecutor executor(ThreadPoolTaskExecutorBuilder builder, TaskDecorator taskDecorator) {
            return builder.threadNamePrefix("GTX-").taskDecorator(taskDecorator).build();
        }


    }


    @RestController
    static class Ctrl {

        @Autowired
        RestTemplate restTemplate;
        @Autowired
        JmsTemplate jmsTemplate;
        @Autowired
        Tracer tracer; //checking auto wiring

        @Autowired
        AsyncTaskExecutor executor;

        @Autowired
        AService aService;

        @Autowired
        FeignTestClient testApiClient;

        @Autowired
        ObservationRegistry observationRegistry;


        @Scheduled(fixedDelay = 10000L)
        @GetMapping("/test0")
        void test0() {
            log.info("test0 - schedule called . Thread {}", Thread.currentThread().getName());
            restTemplate.getForEntity("http://localhost:8081/test1/test0", Void.class);

            executor.execute(() -> {
                log.info("test0 - Running task using scheduler. Thread: {}", Thread.currentThread().getName()); //working
                restTemplate.getForEntity("http://localhost:8081/test1/test0.executor1", Void.class);
                restTemplate.getForEntity("http://localhost:8081/jms", Void.class);
                aService.someAsyncMethod("test0.executor2");
            });

            aService.someAsyncMethod("test0");

//            testApiClient.test1("test 0 using feign client");

        }

        @GetMapping("/test1/{from}")
        void test1(@PathVariable String from) throws CustomException {
            log.info("test1 called from " + from);
            restTemplate.getForEntity("http://localhost:8081/test2/test1", Void.class); //-->it works
            aService.someAsyncMethod("test1");

            if (from.contains("feign")) {
                throw new CustomException("Something");
            }
        }


        @GetMapping("/test2/{from}")
        void test2(@PathVariable String from) {
            log.info("test2 called from " + from);
        }

        @GetMapping("/jms-error-handler")
        void jmsError() {
            log.info("jms -  got some error... handling it on this api");

        }

        @GetMapping("/jms")
        void jms() {
            jmsTemplate.setObservationRegistry(observationRegistry);

            log.info("jms - Queuing message ...");
            aService.someAsyncMethod("jms-start");

            restTemplate.getForEntity("http://localhost:8081/test2/jms", Void.class); //-->it works

            jmsTemplate.convertAndSend("test-queue1", "SOME MESSAGE to queue 1 !!!");
            aService.someAsyncMethod("jms-end");

        }

        @JmsListener(destination = "test-queue1", concurrency = "5")
        void onMessage(TextMessage message) throws JMSException {
            log.info("JMS message received  - queue 1 {}", message.getText());

            jmsTemplate.convertAndSend("test-queue2", "SOME MESSAGE From Test Queue 1 to Queue 2");

            restTemplate.getForEntity("http://localhost:8081/test/jms-onMessage", Void.class); //-->it works -- throws 404
        }


        @JmsListener(destination = "test-queue2", concurrency = "5")
        void onMessage2(TextMessage message) throws JMSException {
            log.info("JMS message received  - queue 2 {}", message.getText());

            restTemplate.getForEntity("http://localhost:8081/test/jms-onMessage", Void.class); //-->it works

            throw new MyException("Some Error");  //-->it also works now !!!
        }


        static class MyException extends RuntimeException {
            final Span span;


            public MyException(String msg) {
                super(msg);
                this.span = ThreadLocalSpan.CURRENT_TRACER.next();
            }
        }

    }

    @Component
    static class AService {
        @Async
        void someAsyncMethod(String from) {

            log.info("async called from {} Thread: {}", from, Thread.currentThread().getName());
        }
    }

    @Component
    static class JmsListenerErrorHandler implements ErrorHandler {

        @Autowired
        RestTemplate restTemplate;

        @Override
        public void handleError(Throwable t) {
            log.error("Got some error {}", t.getMessage());
            if (t.getCause() instanceof Ctrl.MyException || t.getCause() instanceof HttpClientErrorException) {

                Ctrl.MyException mex = (Ctrl.MyException) t.getCause();
                Tracer.SpanInScope scope = null;
                try {
                    scope = Tracing.currentTracer().withSpanInScope(mex.span);

                    log.info("handling error by calling another endpoint ..");

                    restTemplate.getForEntity("http://localhost:8081/test1/jms-handle-error", Void.class); //trace id will get propagated

                    log.info("Finished handling error ");
                } finally {
                    if (scope != null) scope.close();
                }
            }

        }
    }


    @Configuration
    @EnableJms
    static class ActiveMqConfig {


        @Bean
        public DefaultJmsListenerContainerFactory jmsListenerContainerFactory(
                DefaultJmsListenerContainerFactoryConfigurer configurer,
                ConnectionFactory connectionFactory,
                ErrorHandler myErrorHandler) {
            DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
            configurer.configure(factory, connectionFactory);
            factory.setErrorHandler(myErrorHandler);
            return factory;
        }

    }

}
