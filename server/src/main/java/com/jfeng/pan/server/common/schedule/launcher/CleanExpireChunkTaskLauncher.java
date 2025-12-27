package com.jfeng.pan.server.common.schedule.launcher;

import com.jfeng.pan.schedule.ScheduleManager;
import com.jfeng.pan.server.common.schedule.task.CleanExpireChunkFileTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 定期清理过期的文件分片任务触发器
 *
 */
@Component
@Slf4j
public class CleanExpireChunkTaskLauncher implements CommandLineRunner {

    private static final String CRON = "1 0 0 * * ? ";

    @Autowired
    private CleanExpireChunkFileTask task;

    @Autowired
    private ScheduleManager scheduleManager;


    @Override
    public void run(String... args) throws Exception {
        scheduleManager.startTask(task, CRON);
    }
}
