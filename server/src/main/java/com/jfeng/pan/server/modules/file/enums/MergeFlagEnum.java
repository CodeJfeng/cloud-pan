package com.jfeng.pan.server.modules.file.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 文件合并表示枚举类
 */
@AllArgsConstructor
@Getter
public enum MergeFlagEnum {

    /**
     * 不需要合并
     */
    NOT_READY(0),
    /**
     * 需要合并
     */
    READY(1);

    private Integer code;
}
