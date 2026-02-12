package com.jfeng.pan.server.common.stream.consumer;

import com.jfeng.pan.server.common.stream.event.TestEvent;
import com.jfeng.pan.stream.core.AbstractConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

/**
 * 测试消息消费者
 */
@Slf4j
@Component
public class TestConsumer extends AbstractConsumer {

    /**
     * 消费测试消息
     */
    @Bean
    public Consumer<TestEvent> consumeTest(){
        return event ->{
            log.info("{} start consume the message, the message is {}.",
                    Thread.currentThread().getName() ,event.getName());
        };
    }

}
