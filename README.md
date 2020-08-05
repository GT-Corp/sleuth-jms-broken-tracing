# sleuth-jms-broken-tracing

```
2020-08-04 17:55:24.212  INFO [,225c47fb814f6584,225c47fb814f6584,true] 16956 --- [nio-8080-exec-1] sleuth.SleuthApplication                 : Queuing message ...
2020-08-04 17:55:24.282  INFO [,225c47fb814f6584,eac851f1650ae8a6,true] 16956 --- [enerContainer-1] sleuth.SleuthApplication                 : JMS message received SOME MESSAGE !!!
2020-08-04 17:55:24.321  INFO [,225c47fb814f6584,612a7956f6b29a01,true] 16956 --- [nio-8080-exec-3] sleuth.SleuthApplication                 : test1 called  <<<<<<<<< FINE UPTO HERE
2020-08-04 17:55:24.332  INFO [,,,] 16956 --- [enerContainer-1] sleuth.SleuthApplication                 : handling error by calling another endpoint ..     <<<<<<<<< new thread started and lost tracing
2020-08-04 17:55:24.336  INFO [,4c163d0997076729,4c163d0997076729,true] 16956 --- [nio-8080-exec-2] sleuth.SleuthApplication                 : test1 called  <<<<<<<<< new trace id received
```

## Solution (a workaround) found:

See the diff on the PR:

https://github.com/gtiwari333/sleuth-jms-broken-tracing/pull/2/files
