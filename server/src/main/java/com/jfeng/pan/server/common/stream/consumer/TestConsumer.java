package com.jfeng.pan.server.common.stream.consumer;

import com.jfeng.pan.server.common.stream.channel.PanChannel;
import com.jfeng.pan.server.common.stream.event.TestEvent;
import com.jfeng.pan.stream.core.AbstractConsumer;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

/**
 * 测试消息消费者
 */
@Component
public class TestConsumer extends AbstractConsumer {

    /**
     * 消费测试消息
     * @param message
     */
    @StreamListener(PanChannel.TEST_INPUT)
    public void consumeTestConsumer(Message<TestEvent> message){
        printLog(message);
    }

}
