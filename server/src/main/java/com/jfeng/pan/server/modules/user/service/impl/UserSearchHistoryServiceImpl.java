package com.jfeng.pan.server.modules.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jfeng.pan.server.modules.user.context.QueryUserSearchHistoryContext;
import com.jfeng.pan.server.modules.user.entity.RPanUserSearchHistory;
import com.jfeng.pan.server.modules.user.service.IUserSearchHistoryService;
import com.jfeng.pan.server.modules.user.mapper.RPanUserSearchHistoryMapper;
import com.jfeng.pan.server.modules.user.vo.UserSearchHistoryVO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
* @author 16837
* @description 针对表【r_pan_user_search_history(用户搜索历史表)】的数据库操作Service实现
* @createDate 2025-11-06 19:14:11
*/
@Service(value = "userSearchHistoryServiceImpl")
public class UserSearchHistoryServiceImpl extends ServiceImpl<RPanUserSearchHistoryMapper, RPanUserSearchHistory>
    implements IUserSearchHistoryService {

    /**
     * 查询用户的搜索历史记录，默认十条
     *
     * @param context
     * @return
     */
    @Override
    public List<UserSearchHistoryVO> getUserSearchHistory(QueryUserSearchHistoryContext context) {
        return  baseMapper.selectUserSearchHistories(context);
    }
}




