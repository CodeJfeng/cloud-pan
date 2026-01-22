package com.jfeng.pan.schedule.test;

import com.jfeng.pan.schedule.ScheduleManager;
import com.jfeng.pan.schedule.test.config.ScheduleTestConfig;
import com.jfeng.pan.schedule.test.task.SimpleScheduleTask;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * 定时任务单元测试实例
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = ScheduleTestConfig.class)
public class ScheduleTest {
    @Autowired
    private ScheduleManager scheduleManager;

    @Autowired
    private SimpleScheduleTask simpleScheduleTask;

    @Test
    public void testRunScheduleTask() throws InterruptedException {
        String cron = "0/5 * * * * ?";
        String key = scheduleManager.startTask(simpleScheduleTask, cron);
        Thread.sleep(10000);
        cron = "0/1 * * * * ?";
        key = scheduleManager.changeTask(key, cron);
        Thread.sleep(10000);
        scheduleManager.stopTask(key);
   }
}
