package com.jfeng.pan.server.common.stream.consumer.search;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.jfeng.pan.core.utils.IdUtil;
import com.jfeng.pan.server.common.stream.event.search.UserSearchEvent;
import com.jfeng.pan.server.modules.user.entity.RPanUserSearchHistory;
import com.jfeng.pan.server.modules.user.service.IUserSearchHistoryService;
import com.jfeng.pan.stream.core.AbstractConsumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.function.Consumer;

/**
 * 用户搜索事件监听器
 */
@Component
public class UserSearchConsumer extends AbstractConsumer {

    @Autowired
    private IUserSearchHistoryService iUserSearchHistoryService;

    /**
     * 监听用户的搜索事件，将其保存到用户的搜索历史记录中
     * @return
     */
    @Bean
    public Consumer<UserSearchEvent> consumeUserSearch(){
        return event -> {
            RPanUserSearchHistory record = new RPanUserSearchHistory();

            record.setId(IdUtil.get());
            record.setUserId(event.getUserId());
            record.setSearchContent(event.getKeyword());
            record.setCreateTime(new Date());
            record.setUpdateTime(new Date());

            try{
                iUserSearchHistoryService.save(record);
            }catch (DuplicateKeyException e){
                LambdaUpdateWrapper<RPanUserSearchHistory> wrapper = new LambdaUpdateWrapper<>();
                wrapper.eq(RPanUserSearchHistory::getUserId, event.getUserId());
                wrapper.eq(RPanUserSearchHistory::getSearchContent, event.getKeyword());
                wrapper.set(RPanUserSearchHistory::getUpdateTime, new Date());
                iUserSearchHistoryService.update(wrapper);
            }
        };
    }
}
