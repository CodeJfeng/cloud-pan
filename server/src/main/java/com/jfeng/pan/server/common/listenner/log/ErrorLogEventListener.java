package com.jfeng.pan.server.common.listenner.log;

import com.jfeng.pan.core.utils.IdUtil;
import com.jfeng.pan.server.common.event.log.ErrorLogEvent;
import com.jfeng.pan.server.modules.log.entity.RPanErrorLog;
import com.jfeng.pan.server.modules.log.service.IErrorLogService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 系统错误的日志监听器
 */
@Component
public class ErrorLogEventListener {

    private IErrorLogService iErrorLogService;

    /**
     * 监听错误日志事件，并保存到数据库中
     * @param event
     */
    @EventListener(ErrorLogEvent.class)
    public void saveErrorLog(ErrorLogEvent event){
        RPanErrorLog record = new RPanErrorLog();
        record.setId(IdUtil.get());
        record.setLogContent(event.getErrorMsg());
        record.setLogStatus(0);
        record.setCreateTime(new Date());
        record.setCreateUser(event.getUserId());
        record.setUpdateTime(new Date());
        record.setUpdateUser(event.getUserId());
        iErrorLogService.save(record);
    }
}
