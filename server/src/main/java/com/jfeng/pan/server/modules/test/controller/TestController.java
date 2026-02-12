package com.jfeng.pan.server.modules.test.controller;

import com.jfeng.pan.core.response.R;
import com.jfeng.pan.server.common.annotation.LoginIgnore;
import com.jfeng.pan.server.common.event.test.TestEvent;
import com.jfeng.pan.server.common.stream.channel.PanChannel;
import com.jfeng.pan.stream.core.IStreamProducer;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 测试处理器
 */
@RestController
public class TestController implements ApplicationContextAware {

    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Autowired
    @Qualifier(value = "defaultStreamProducer")
    private IStreamProducer producer;

    /**
     * 测试事件发布
     *
     * @return
     */
    @GetMapping("test")
    @LoginIgnore
    public R test() {
        applicationContext.publishEvent(new TestEvent(this, "test"));
        return R.success();
    }


    /**
     * 测试流事件发布
     *
     * @return
     */
    @GetMapping("stream/test")
    @LoginIgnore
    public R streamTest(String name) {
        com.jfeng.pan.server.common.stream.event.TestEvent testEvent = new com.jfeng.pan.server.common.stream.event.TestEvent();
        testEvent.setName(name);
        producer.sendMessage(PanChannel.TEST_OUTPUT, testEvent);
        return R.success();
    }


}
