package sleuth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@Slf4j
@Aspect
class CacheAspect {

    final CacheManager cacheManager;

    CacheAspect(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    static String toString(Object value) {
        return value == null ? "" : value.toString();
    }

    @Around("execution(* sleuth.MyService.getValue(..))")
    String cacheGetValue(ProceedingJoinPoint jp) throws Throwable {
        log.info("inside aop jp");
        String key = Stream.of(jp.getArgs()).map(it -> toString(it)).collect(Collectors.joining(","));

        Cache cache = cacheManager.getCache("getValue");

        Cache.ValueWrapper respFromCache = cache.get(key);
        if (respFromCache != null) {
            return (String) respFromCache.get();
        }

        String resp = (String) jp.proceed();
        cache.put(key, resp);
        return resp;
    }

}

@RestController
@RequiredArgsConstructor
class CtrlCache {

    final MyService util;

    @GetMapping("/cache")
    String x() {
      return util.getValue();
    }
}

