package sleuth;

import org.slf4j.*;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.sleuth.instrument.async.LazyTraceThreadPoolTaskExecutor;
import org.springframework.context.annotation.*;
import org.springframework.jms.annotation.*;
import org.springframework.jms.config.*;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.util.ErrorHandler;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import javax.jms.*;
import java.util.concurrent.Executor;

@SpringBootApplication
public class SleuthApplication {

    static Logger log = LoggerFactory.getLogger(SleuthApplication.class);

    public static void main(String[] args) {  new SpringApplication(SleuthApplication.class).run(args); }

    @Bean
    RestTemplate restTemplate() {  return new RestTemplate();  }

    @RestController
    static class Ctrl {

        @Autowired RestTemplate restTemplate;
        @Autowired JmsTemplate jmsTemplate;
        @Autowired Executor executor;

        @GetMapping("/test")
        void test() {
            log.info("test1 called");
        }

        @GetMapping("/jms")
        void jms() {
            log.info("Queuing message ...");
            jmsTemplate.convertAndSend("test-queue", "SOME MESSAGE !!!");
        }

        @JmsListener(destination = "test-queue", concurrency = "5")
        void onMessage(TextMessage message) throws JMSException {
            log.info("JMS message received {}", message.getText());

            restTemplate.getForEntity("http://localhost:8080/test", Void.class); //-->it works

            executor.execute(() -> log.info("Im inside thread 2")); //it works

            throw new MyException("Some Error");  //-->it doesn't
        }

        static class MyException extends RuntimeException {
            public MyException(String msg) { super(msg); }
        }

    }

    @Component
    static class JmsListenerErrorHandler implements ErrorHandler {

        @Autowired RestTemplate restTemplate;

        @Override
        public void handleError(Throwable t) {
            log.info("handling error by calling another endpoint .."); //1....tracing is lost here
            restTemplate.getForEntity("http://localhost:8080/test", Void.class);
        }
    }


    @Configuration
    @EnableJms
    static class ActiveMqConfig implements JmsListenerConfigurer {

        @Autowired ErrorHandler jmsListenerErrorHandler;

        @Autowired ConnectionFactory connectionFactory;

        @Autowired BeanFactory beanFactory;

        @Override
        public void configureJmsListeners(JmsListenerEndpointRegistrar registrar) {
            registrar.setContainerFactory(containerFactory());
        }


        @Bean
        public Executor executor() {
            ThreadPoolTaskExecutor threadPoolTaskExecutor = new ThreadPoolTaskExecutor();
            threadPoolTaskExecutor.setCorePoolSize(1);
            threadPoolTaskExecutor.setMaxPoolSize(1);
            threadPoolTaskExecutor.initialize();

            return new LazyTraceThreadPoolTaskExecutor(beanFactory, threadPoolTaskExecutor);
        }

        @Bean
        JmsListenerContainerFactory<?> containerFactory( ) {
            DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
            factory.setConnectionFactory(connectionFactory);
            factory.setErrorHandler(jmsListenerErrorHandler);
            factory.setTaskExecutor(executor());   //DOES NOT WORK !!!
            return factory;
        }
    }


}
