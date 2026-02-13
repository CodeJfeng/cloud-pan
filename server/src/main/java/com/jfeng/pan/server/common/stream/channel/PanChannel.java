package com.jfeng.pan.server.common.stream.channel;

import org.springframework.cloud.stream.annotation.Input;
import org.springframework.cloud.stream.annotation.Output;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.SubscribableChannel;

/**
 * 事件通道定义类
 */
public interface PanChannel {

    String TEST_INPUT = "testInput";

    String TEST_OUTPUT = "testOutput";

    String ERROR_LOG_OUT = "errorLog-out-0";
    String ERROR_LOG_IN = "consumeErrorLog";

    String DELETE_FILE_OUT = "deleteFile-out-0";
    String DELETE_FILE_IN = "consumeDeleteFile";

    String FILE_RESTORE_OUT = "fileRestore-out-0";
    String FILE_RESTORE_IN = "consumeFileRestore";

    String Physical_Delete_File_OUT = "physicalDeleteFile-out-0";
    String Physical_Delete_File_IN = "consumerPhysicalDeleteFile";

    String User_Search_OUT = "userSearch-out-0";
    String User_Search_IN = "consumeUserSearch";

//    /**
//     * 测试输入通道
//     * @return
//     */
//    @Input(TEST_INPUT)
//    SubscribableChannel testInput();
//
//    /**
//     * 测试输入通道
//     * @return
//     */
//    @Output(TEST_OUTPUT)
//    MessageChannel testOutput();
}
