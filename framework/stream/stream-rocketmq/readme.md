# 启动RocketMQ流程
- 在Bin目录下首先启动Name Server
  ```bash
  start mqnamesrv.cmd
  ```
- 启动 Broker（关键：必须带参数）
  ```bash
    start mqbroker.cmd -n 127.0.0.1:9876 autoCreateTopicEnable=true
  ```