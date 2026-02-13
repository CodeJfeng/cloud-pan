package com.jfeng.pan.server.common.stream.event.log;

import lombok.*;
import org.springframework.context.ApplicationEvent;

import java.io.Serial;
import java.io.Serializable;

/**
 * 错误日志事件
 */
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class ErrorLogEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = -3846438249645L;

    /**
     * 错误日志的内容
     */
    private String errorMsg;

    /**
     * 当前登录的用户ID
     */
    private Long userId;

    public ErrorLogEvent(String errorMsg, Long userId){
        this.errorMsg = errorMsg;
        this.userId = userId;
    }

}
