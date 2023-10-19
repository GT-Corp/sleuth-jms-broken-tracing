package sleuth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MyService {

    public String getValue() {
        log.info("Ran getValue");
        return "THE VALUE";
    }
}
