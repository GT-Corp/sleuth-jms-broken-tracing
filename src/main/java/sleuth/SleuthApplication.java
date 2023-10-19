package sleuth;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.propagation.ThreadLocalSpan;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import jakarta.jms.TextMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.annotation.JmsListenerConfigurer;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.config.JmsListenerContainerFactory;
import org.springframework.jms.config.JmsListenerEndpointRegistrar;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.util.ErrorHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
@EnableScheduling
@EnableCaching
public class SleuthApplication {

    static Logger log = LoggerFactory.getLogger(SleuthApplication.class);

    public static void main(String[] args) {
        new SpringApplication(SleuthApplication.class).run(args);
    }

    @Bean
    RestTemplate restTemplate(RestTemplateBuilder rb) {
        return rb.build();
    }

    @Configuration
    class AsyncConfig {

        @Bean
        public TaskDecorator decorator() {
            return new ContextPropagatingTaskDecorator();
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
//        @Qualifier(value = "asyncExecutorPool2")
        ThreadPoolTaskExecutor scheduler;


        //        @Scheduled(fixedDelay = 40000L)
        void callTest3() {
            log.info("schedule called");
            restTemplate.getForEntity("http://localhost:8081/test3", Void.class);

            scheduler.submit(() -> {
                log.info("Running task using scheduler ");
                restTemplate.getForEntity("http://localhost:8081/jms", Void.class);
            });
        }

        @GetMapping("/test")
        void test() {
            log.info("test1 called");
            restTemplate.getForEntity("http://localhost:8081/test2", Void.class); //-->it works

        }

        @GetMapping("/test2")
        void test2() {
            log.info("test2 called");
            restTemplate.getForEntity("http://localhost:8081/test3", Void.class); //-->it works
        }

        @Async
        @GetMapping("/test3")
        void test3() {
            log.info("test3 async called");
        }

        @GetMapping("/jms")
        void jms() {
            log.info("Queuing message ...");
            restTemplate.getForEntity("http://localhost:8081/test2", Void.class); //-->it works

            /*
            //jms integration is not working yet
            https://github.com/micrometer-metrics/micrometer/issues/4202
            https://github.com/spring-projects/spring-framework/issues/30335
             */
            jmsTemplate.convertAndSend("test-queue", "SOME MESSAGE !!!");
        }

        @JmsListener(destination = "test-queue", concurrency = "5")
        void onMessage(TextMessage message) throws JMSException {
            log.info("JMS message received {}", message.getText());

            restTemplate.getForEntity("http://localhost:8081/test", Void.class); //-->it works

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
    static class JmsListenerErrorHandler implements ErrorHandler {

        @Autowired
        RestTemplate restTemplate;

        @Override
        public void handleError(Throwable t) {
            if (t.getCause() instanceof Ctrl.MyException) {

                Ctrl.MyException mex = (Ctrl.MyException) t.getCause();
                Tracer.SpanInScope scope = null;
                try {
                    scope = Tracing.currentTracer().withSpanInScope(mex.span);

                    log.info("handling error by calling another endpoint ..");

                    restTemplate.getForEntity("http://localhost:8081/test", Void.class); //trace id will get propagated

                    log.info("Finished handling error ");
                } finally {
                    if (scope != null) scope.close();
                }
            }

        }
    }


    @Configuration
    @EnableJms
    static class ActiveMqConfig implements JmsListenerConfigurer {

        @Autowired
        ErrorHandler jmsListenerErrorHandler;

        @Autowired
        ConnectionFactory connectionFactory;

        @Override
        public void configureJmsListeners(JmsListenerEndpointRegistrar registrar) {
            registrar.setContainerFactory(containerFactory());
        }

        @Bean
        JmsListenerContainerFactory<?> containerFactory() {
            DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
            factory.setConnectionFactory(connectionFactory);
            factory.setErrorHandler(jmsListenerErrorHandler);
            return factory;
        }
    }


}
