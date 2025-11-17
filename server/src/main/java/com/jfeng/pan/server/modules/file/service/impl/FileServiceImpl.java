package com.jfeng.pan.server.modules.file.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jfeng.pan.server.modules.file.entity.RPanFile;
import com.jfeng.pan.server.modules.file.service.IFileService;
import com.jfeng.pan.server.modules.file.mapper.RPanFileMapper;
import org.springframework.stereotype.Service;

/**
* @author 16837
* @description 针对表【r_pan_file(物理文件信息表)】的数据库操作Service实现
* @createDate 2025-11-06 19:22:58
*/
@Service(value = "userFileService")
public class FileServiceImpl extends ServiceImpl<RPanFileMapper, RPanFile> implements IFileService {


}




