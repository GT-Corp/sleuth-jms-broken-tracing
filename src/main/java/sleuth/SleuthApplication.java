package sleuth;

import brave.Span;
import brave.Tracing;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.*;
import org.springframework.jms.annotation.*;
import org.springframework.jms.config.*;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.ErrorHandler;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import javax.jms.*;

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

            throw new MyException("Some Error");  //-->it also works now !!!
        }

        static class MyException extends RuntimeException {
            final Span span;

            public MyException(String msg) {
                super(msg);
                this.span = Tracing.currentTracer().currentSpan();
            }
        }

    }

    @Component
    static class JmsListenerErrorHandler implements ErrorHandler {

        @Autowired RestTemplate restTemplate;

        @Override
        public void handleError(Throwable t) {
            if(t.getCause() instanceof Ctrl.MyException){

                Ctrl.MyException mex = (Ctrl.MyException) t.getCause();

                Tracing.currentTracer().withSpanInScope(mex.span);

                log.info("handling error by calling another endpoint ..");

                restTemplate.getForEntity("http://localhost:8080/test", Void.class); //trace id will get propagated

                log.info("Finished handling error " );
            }

        }
    }


    @Configuration
    @EnableJms
    static class ActiveMqConfig implements JmsListenerConfigurer {

        @Autowired ErrorHandler jmsListenerErrorHandler;

        @Autowired ConnectionFactory connectionFactory;

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
