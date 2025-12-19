package com.jfeng.pan.server.modules.file.context;

import lombok.Data;

import javax.servlet.http.HttpServletResponse;
import java.io.Serial;
import java.io.Serializable;

/**
 * 文件预览的上下文对象
 */
@Data
public class FilePreviewContext implements Serializable {

    @Serial
    private static final long  serialVersionUID = -457351244534L;

    /**
     * 文件ID
     */
    private Long fileId;

    /**
     * 请求响应对象
     */
    private HttpServletResponse response;

    /**
     * 当前的登录的用户ID
     */
    private Long userId;
}
