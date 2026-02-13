package com.jfeng.pan.server.common.stream.consumer.log;

import com.jfeng.pan.core.utils.IdUtil;
import com.jfeng.pan.server.common.stream.event.log.ErrorLogEvent;
import com.jfeng.pan.server.modules.log.entity.RPanErrorLog;
import com.jfeng.pan.server.modules.log.service.IErrorLogService;
import com.jfeng.pan.stream.core.AbstractConsumer;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.function.Consumer;

/**
 * 系统错误的日志监听器
 */
@Component
public class ErrorLogEventConsumer extends AbstractConsumer {

    private IErrorLogService iErrorLogService;

    /**
     * 监听错误日志事件，并保存到数据库中
     * @return
     */
    @Bean
    public Consumer<ErrorLogEvent> consumeErrorLog(){
        return event -> {
            RPanErrorLog record = new RPanErrorLog();
            record.setId(IdUtil.get());
            record.setLogContent(event.getErrorMsg());
            record.setLogStatus(0);
            record.setCreateTime(new Date());
            record.setCreateUser(event.getUserId());
            record.setUpdateTime(new Date());
            record.setUpdateUser(event.getUserId());
            iErrorLogService.save(record);
        };
    }
}
