package com.jfeng.pan.server.modules.test.controller;

import com.jfeng.pan.core.response.R;
import com.jfeng.pan.server.common.annotation.LoginIgnore;
import com.jfeng.pan.server.common.event.test.TestEvent;
import org.springframework.beans.BeansException;
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

}
