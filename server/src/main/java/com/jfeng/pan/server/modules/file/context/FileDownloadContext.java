package com.jfeng.pan.server.modules.file.context;

import lombok.Data;

import javax.servlet.http.HttpServletResponse;
import java.io.Serial;
import java.io.Serializable;

/**
 * 文件下载的上下文实体对象
 */
@Data
public class FileDownloadContext implements Serializable {

    @Serial
    private static final long serialVersionUID = -7907239620654501566L;

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
