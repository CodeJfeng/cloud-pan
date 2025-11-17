package com.jfeng.pan.server.modules.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jfeng.pan.server.modules.user.entity.RPanUserSearchHistory;
import com.jfeng.pan.server.modules.user.service.IUserSearchHistoryService;
import com.jfeng.pan.server.modules.user.mapper.RPanUserSearchHistoryMapper;
import org.springframework.stereotype.Service;

/**
* @author 16837
* @description 针对表【r_pan_user_search_history(用户搜索历史表)】的数据库操作Service实现
* @createDate 2025-11-06 19:14:11
*/
@Service(value = "userSearchHistoryServiceImpl")
public class UserSearchHistoryServiceImpl extends ServiceImpl<RPanUserSearchHistoryMapper, RPanUserSearchHistory>
    implements IUserSearchHistoryService {

}




