package sleuth.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "test-service", url = "${feign-clients.test-service.url}")
public interface FeignTestClient {
    @GetMapping("/test1/{from}")
    void test1(@PathVariable String from);
}
