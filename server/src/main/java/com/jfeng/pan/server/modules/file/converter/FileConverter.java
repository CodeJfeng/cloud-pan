package com.jfeng.pan.server.modules.file.converter;

import com.jfeng.pan.server.modules.file.context.CreateFolderContext;
import com.jfeng.pan.server.modules.file.context.UpdateFilenameContext;
import com.jfeng.pan.server.modules.file.po.CreateFolderPO;
import com.jfeng.pan.server.modules.file.po.UpdateFilenamePO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface FileConverter {

    @Mapping(target = "parentId", expression = "java(com.jfeng.pan.core.utils.IdUtil.decrypt(createFolderPO.getParentId()))")
    @Mapping(target = "userId", expression = "java(com.jfeng.pan.server.common.utils.UserIdUtil.get())")
    CreateFolderContext createFolderPO2CreateFolderContext(CreateFolderPO createFolderPO);

    @Mapping(target = "fileId", expression = "java(com.jfeng.pan.core.utils.IdUtil.decrypt(updateFilenamePO.getFileId()))")
    @Mapping(target = "userId", expression = "java(com.jfeng.pan.server.common.utils.UserIdUtil.get())")
    UpdateFilenameContext updateFilenamePO2UpdateFilenameContext(UpdateFilenamePO updateFilenamePO);
}
