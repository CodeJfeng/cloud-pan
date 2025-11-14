package com.jfeng.pan.schedule;

import com.jfeng.pan.core.exception.RPanFrameworkException;
import com.jfeng.pan.core.utils.UUIDUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 定时任务管理器
 * 1、启动并创建一个定时任务
 * 2、停止一个定时任务
 * 3、更新一个定时任务
 */
@Component
@Slf4j
public class ScheduleManager {

    @Autowired
    private ThreadPoolTaskScheduler taskScheduler;

    /**
     * 内部正在执行的定时任务缓存
     */
    private final Map<String, Object> cache = new ConcurrentHashMap<>();

    /**
     * 启动一个定时任务
     * @param scheduleTask  定时任务的实现类
     * @param cron          定时任务的cron表达式
     * @return 返回任务在内存的UUID唯一标识
     */
    public String startTask(ScheduleTask scheduleTask, String cron){
        ScheduledFuture<?> scheduledFuture = taskScheduler.schedule(scheduleTask, new CronTrigger(cron));
        String key = UUIDUtil.getUUID();
        ScheduleTaskHolder scheduleTaskHolder = new ScheduleTaskHolder(scheduleTask, scheduledFuture);
        cache.put(key,scheduleTaskHolder);
        log.info("{}启动成功，唯一标识为{}", scheduleTask, key);
        return key;
    }

    /**
     * 停止一个定时任务
     * @param key 定时任务的唯一标识
     */
    public void stopTask(String key){
        if(StringUtils.isBlank(key)){
            return;
        }
        ScheduleTaskHolder scheduleTaskHolder = (ScheduleTaskHolder) cache.get(key);
        if(Objects.isNull(scheduleTaskHolder)){
            return;
        }
        ScheduledFuture<?> scheduledFuture = scheduleTaskHolder.getScheduledFuture();
        boolean cancel = scheduledFuture.cancel(true);
        if(cancel){
            log.info("{} 停止成功！唯一标识为：{}", scheduleTaskHolder.getScheduleTask().getName(), key);
        }else{
            log.error("{} 停止失败！唯一标识为：{}", scheduleTaskHolder.getScheduleTask().getName(), key);
        }
    }

    /**
     * 更新定时任务的执行时间
     * @param key 定时任务的唯一标识
     * @param cron 新的cron表达式
     * @return 返回新的UUID
     */
    public String changeTask(String key, String cron){
        if(StringUtils.isAnyBlank(key, cron)){
            throw new RPanFrameworkException("定时任务的唯一标识以及新的执行表达式不能为空");
        }
        ScheduleTaskHolder scheduleTaskHolder = (ScheduleTaskHolder) cache.get(key);
        if(Objects.isNull(scheduleTaskHolder)){
            throw new RPanFrameworkException(key+"唯一标识不存在");
        }
        stopTask(key);
        return startTask(scheduleTaskHolder.getScheduleTask(), cron);
    }

}
