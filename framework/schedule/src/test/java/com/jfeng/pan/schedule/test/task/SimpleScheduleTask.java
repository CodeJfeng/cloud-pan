package com.jfeng.pan.schedule.test.task;

import com.jfeng.pan.schedule.ScheduleTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SimpleScheduleTask implements ScheduleTask {

    @Override
    public String getName(){
        return "测试定时任务";
    }

    @Override
    public void run(){
        log.info("{}正在执行", getName());
    }
}
