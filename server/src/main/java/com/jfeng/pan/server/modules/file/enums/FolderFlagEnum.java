package com.jfeng.pan.server.modules.file.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 文件类型枚举类
 */
@AllArgsConstructor
@Getter
public enum FolderFlagEnum {

    /**
     * 非文件夹
     */
    NO(0),

    /**
     * 是文件夹
     */
    YES(1);

    private Integer code;

}
