package com.jfeng.pan.server.modules.file.constants;

import lombok.Data;

/**
 * 文件模块常量类
 */
public interface FileConstants {
    /**
     * 顶级父文件ID
     */
    Long TOP_PARENT_ID = 0L;

    /**
     * 根文件名称
     */
    String ALL_FILE_CN_STR = "全部文件";

    /**
     * 左括号
     */
    String LEFT_PARENTHESIS_STR = "(";

    /**
     * 右括号
     */
    String RIGHT_PARENTHESIS_STR = ")";


    /**
     * 所有文件类型的标识
     */
    String ALL_FILE_TYPE = "-1";

    String CONTENT_TYPE_STR = "Content-Type";

    /**
     * 文件内容的部署方式
     */
    String CONTENT_DISPOSITION_STR = "Content-Disposition";

    /**
     * 以附件的方式下载
     */
    String CONTENT_DISPOSITION_VALUE_PREFIX_STR = "attachment;fileName=";

    String GB2312_STR = "GB2312";

    String IOS_8859_1_STR = "ISO-8859-1";

}
