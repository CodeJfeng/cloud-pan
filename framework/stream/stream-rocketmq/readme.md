# 启动RocketMQ流程
- 在Bin目录下首先启动Name Server
  ```bash
  cd D:\app\major\rocketmq-all-5.4.0\bin
  start mqnamesrv.cmd
  ```
- 启动 Broker（关键：必须带参数）
  ```bash
  start mqbroker.cmd -n 127.0.0.1:9876 autoCreateTopicEnable=true
  ```
  
```angular2html
start mqbroker.cmd -n localhost:9876 -c ..\conf\broker.conf autoCreateTopicEnable=true
```