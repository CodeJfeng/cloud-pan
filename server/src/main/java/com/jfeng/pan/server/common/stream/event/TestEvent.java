package com.jfeng.pan.server.common.stream.event;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 测试事件实体
 */
@Data
public class TestEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = -218124454835L;


    /**
     * 消息属性名称
     */
    private String name;

}
