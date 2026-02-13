package com.jfeng.pan.server.common.stream.consumer.share;

import com.jfeng.pan.server.common.stream.event.file.DeleteFileEvent;
import com.jfeng.pan.server.common.stream.event.file.FileRestoreEvent;
import com.jfeng.pan.server.modules.file.entity.RPanUserFile;
import com.jfeng.pan.server.modules.file.enums.DelFlagEnum;
import com.jfeng.pan.server.modules.file.service.IUserFileService;
import com.jfeng.pan.server.modules.share.service.IShareService;
import com.jfeng.pan.stream.core.AbstractConsumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 监听文件状态变更导致分享状态变更的处理器
 */
@Component
public class ShareStatusChangeConsumer extends AbstractConsumer {

    @Autowired
    private IUserFileService iUserFileService;

    @Autowired
    private IShareService iShareService;

    /**
     * 监听文件被删除之后，刷新所有受影响的分享的状态
     *
     * @return
     */
    @Bean
    public Consumer<DeleteFileEvent> consumeDeleteFile() {
        return event -> {
            List<Long> fileIdList = event.getFileIdList();
            if (CollectionUtils.isEmpty(fileIdList)) {
                return;
            }
            List<RPanUserFile> allRecords = iUserFileService.findAllFileRecordsByFileIdList(fileIdList);
            List<Long> allAvailableFileIdList = allRecords.stream()
                    .filter(record -> Objects.equals(record.getDelFlag(), DelFlagEnum.NO.getCode()))
                    .map(RPanUserFile::getFileId)
                    .collect(Collectors.toList());
            allAvailableFileIdList.addAll(fileIdList);
            iShareService.refreshShareStatus(allAvailableFileIdList);
        };
    }

    /**
     * 监听文件被还原后，刷新所有受影响的分享的状态
     *
     * @return
     */
    @Bean
    public Consumer<FileRestoreEvent> consumeFileRestore() {
        return event ->{
            List<Long> fileIdList = event.getFileListId();
            if (CollectionUtils.isEmpty(fileIdList)) {
                return;
            }
            List<RPanUserFile> allRecords = iUserFileService.findAllFileRecordsByFileIdList(fileIdList);
            List<Long> allAvailableFileIdList = allRecords.stream()
                    .filter(record -> Objects.equals(record.getDelFlag(), DelFlagEnum.NO.getCode()))
                    .map(RPanUserFile::getFileId)
                    .collect(Collectors.toList());
            allAvailableFileIdList.addAll(fileIdList);
            iShareService.refreshShareStatus(allAvailableFileIdList);
        };
    }

}
