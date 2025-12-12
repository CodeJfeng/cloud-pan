package com.jfeng.pan.server.modules.log.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jfeng.pan.server.modules.log.entity.RPanErrorLog;
import com.jfeng.pan.server.modules.log.service.IErrorLogService;
import com.jfeng.pan.server.modules.log.mapper.RPanErrorLogMapper;
import org.springframework.stereotype.Service;

/**
* @author 16837
* @description 针对表【r_pan_error_log(错误日志表)】的数据库操作Service实现
* @createDate 2025-11-06 19:23:30
*/
@Service
public class ErrorLogServiceImpl extends ServiceImpl<RPanErrorLogMapper, RPanErrorLog>
    implements IErrorLogService {

}




