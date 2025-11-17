package com.jfeng.pan.server.modules.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jfeng.pan.core.exception.RPanBusinessException;
import com.jfeng.pan.core.response.ResponseCode;
import com.jfeng.pan.core.utils.IdUtil;
import com.jfeng.pan.core.utils.PasswordUtil;
import com.jfeng.pan.server.modules.file.constants.FileConstants;
import com.jfeng.pan.server.modules.file.context.CreateFolderContext;
import com.jfeng.pan.server.modules.file.service.IUserFileService;
import com.jfeng.pan.server.modules.user.context.UserRegisterContext;
import com.jfeng.pan.server.modules.user.converter.UserConverter;
import com.jfeng.pan.server.modules.user.entity.RPanUser;
import com.jfeng.pan.server.modules.user.service.IUserService;
import com.jfeng.pan.server.modules.user.mapper.RPanUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Objects;

/**
* @author 16837
* @description 针对表【r_pan_user(用户信息表)】的数据库操作Service实现
* @createDate 2025-11-06 19:14:11
*/
@Service(value = "userServiceImpl")
public class UserServiceImpl extends ServiceImpl<RPanUserMapper, RPanUser> implements IUserService {

    @Autowired
    private UserConverter userConverter;

    @Autowired
    private IUserFileService iUserFileService;
    /**
     * 用户注册业务实现
     * 需要实现的功能点：
     * 1、注册用户信息
     * 2、创建新用户的根本目录信息
     * 需要实现的技术难点
     * 1、该业务是幂等的
     * 2、要保证userID是唯一的
     * 实现技术难点的处理方案
     * 1、幂等性通过数据库表对用户名字字段添加唯一索引，我们上游业务捕获对应的冲突异常，转化返回
     * 2、数据库表对用户名字字段添加唯一索引
     * @param userRegisterContext 用户信息
     * @return
     */
    @Override
    public Long register(UserRegisterContext userRegisterContext) {
        assembleUserEntity(userRegisterContext);
        doRegister(userRegisterContext);
        createUserRootFolder(userRegisterContext);
        return userRegisterContext.getEntity().getUserId();
    }


    /****************************************************** private ***************************************************************/

    /**
     * 创建用户的根目录信息
     * @param userRegisterContext
     */
    private void createUserRootFolder(UserRegisterContext userRegisterContext) {
        CreateFolderContext createFolderContext = new CreateFolderContext();
        createFolderContext.setParentId(FileConstants.TOP_PARENT_ID);
        createFolderContext.setUserId(userRegisterContext.getEntity().getUserId());
        createFolderContext.setFolderName(FileConstants.ALL_FILE_CN_STR);
        iUserFileService.createFolder(createFolderContext);
    }

    /**
     * 实现注册用户的业务
     * 需要捕获数据库的唯一索引冲突异常，来实现全局用户唯一
     * @param userRegisterContext 用户注册上下文
     */
    private void doRegister(UserRegisterContext userRegisterContext) {
        RPanUser entity = userRegisterContext.getEntity();
        if(Objects.nonNull(entity)){
            try {
                if(!save(entity)){
                    throw new RPanBusinessException("用户注册失败");
                }
            }catch (DuplicateKeyException duplicateKeyException){
                throw new RPanBusinessException("用户已存在");
            }
            return;
        }
        throw new RPanBusinessException(ResponseCode.ERROR);
    }

    /**
     *  实体转化
     *  有上下文信息转化成用户实体，封装进上下文
     * @param userRegisterContext 用户上下文信息
     */
    private void assembleUserEntity(UserRegisterContext userRegisterContext) {
        RPanUser entity = userConverter.userRegisterContext2RPanUser(userRegisterContext);
        String salt = PasswordUtil.getSalt(),
             dbPassword = PasswordUtil.encryptPassword(salt, userRegisterContext.getPassword());
        entity.setUserId(IdUtil.get());
        entity.setSalt(salt);
        entity.setPassword(dbPassword);
        entity.setCreateTime(new Date());
        entity.setUpdateTime(new Date());
        userRegisterContext.setEntity(entity);
    }

}




