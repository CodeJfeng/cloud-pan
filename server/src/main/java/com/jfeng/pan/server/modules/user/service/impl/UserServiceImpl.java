package com.jfeng.pan.server.modules.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jfeng.pan.cache.core.constants.CacheConstants;
import com.jfeng.pan.core.exception.RPanBusinessException;
import com.jfeng.pan.core.response.ResponseCode;
import com.jfeng.pan.core.utils.IdUtil;
import com.jfeng.pan.core.utils.JwtUtil;
import com.jfeng.pan.core.utils.PasswordUtil;
import com.jfeng.pan.server.modules.file.constants.FileConstants;
import com.jfeng.pan.server.modules.file.context.CreateFolderContext;
import com.jfeng.pan.server.modules.file.service.IUserFileService;
import com.jfeng.pan.server.modules.user.constants.UserConstants;
import com.jfeng.pan.server.modules.user.context.UserLoginContext;
import com.jfeng.pan.server.modules.user.context.UserRegisterContext;
import com.jfeng.pan.server.modules.user.converter.UserConverter;
import com.jfeng.pan.server.modules.user.entity.RPanUser;
import com.jfeng.pan.server.modules.user.service.IUserService;
import com.jfeng.pan.server.modules.user.mapper.RPanUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
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

    @Autowired
    private CacheManager cacheManager;

    /**
     * 用户注册业务的实现
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
     * @return 注册成功的用户ID
     * @throws RPanBusinessException 标识业务注册失败异常
     */
    @Override
    public Long register(UserRegisterContext userRegisterContext) {
        assembleUserEntity(userRegisterContext);
        doRegister(userRegisterContext);
        createUserRootFolder(userRegisterContext);
        return userRegisterContext.getEntity().getUserId();
    }

    /**
     * <p>
     * 用户登录业务的实现
     * 需要实现的功能：
     * 1、用户的登录信息校验
     * 2、生成具有时效性的accessToken
     * 3、将accessToken缓存起来，实现单机版登录
     * </p>
     *
     * @param userLoginContext 用户信息
     * @return
     */
    @Override
    public String login(UserLoginContext userLoginContext) {
        checkLoginInfo(userLoginContext);
        generateAndSaveAccessToken(userLoginContext);
        return userLoginContext.getAccessToken();
    }

    /**
     * <p>
     * 用户退出登录的业务实现
     * 1、清除用户的登录凭证
     * </p>
     * @param userId 用户id
     */
    @Override
    public void exit(Long userId) {
        try {
            Cache cache = cacheManager.getCache(CacheConstants.R_PAN_CACHE_NAME);
            assert cache != null;
            cache.evict(UserConstants.USER_LOGIN_PREFIX+userId);
        }catch (Exception e){
            e.printStackTrace();
            throw new RPanBusinessException("用户登录失败");
        }
    }


/****************************************************** private ***************************************************************/

    /**
     * 生成accessToken并保存凭证
     */
    private void generateAndSaveAccessToken(UserLoginContext userLoginContext) {
        RPanUser entity = userLoginContext.getEntity();
        String token = JwtUtil.generateToken(entity.getUsername(), UserConstants.LOGIN_USER_ID, entity.getUserId(), UserConstants.ONE_DAY_LONG);
        Cache cache = cacheManager.getCache(CacheConstants.R_PAN_CACHE_NAME);
        assert cache != null;
        cache.put(UserConstants.USER_LOGIN_PREFIX+entity.getUserId(), token);
        userLoginContext.setAccessToken(token);
    }


    /**
     * 校验用户和密码
     * @param userLoginContext
     */
    private void checkLoginInfo(UserLoginContext userLoginContext) {
        String username = userLoginContext.getUsername();
        String password = userLoginContext.getPassword();
        RPanUser entity =  getRPanUserByUsername(username);
        if(Objects.isNull(entity)){
            throw new RPanBusinessException("用户名称不存在");
        }
        String salt = entity.getSalt();
        String encPassword = PasswordUtil.encryptPassword(salt, password);
        String dbPassword = entity.getPassword();
        if(!encPassword.equals(dbPassword)){
            throw new RPanBusinessException("密码信息不正确");
        }
        userLoginContext.setEntity(entity);
    }

    /**
     * 通过用户名获取用户实体
     * 采用Lambda表达式，杜绝硬编码的查询形式
     * @param username 用户名
     * @return RPanUser
     */
    private RPanUser getRPanUserByUsername(String username) {
        LambdaQueryWrapper<RPanUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RPanUser::getUsername, username);
        return getOne(wrapper);
    }



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




