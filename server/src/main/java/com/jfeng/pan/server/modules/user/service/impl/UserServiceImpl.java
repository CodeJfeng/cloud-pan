package com.jfeng.pan.server.modules.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jfeng.pan.cache.core.constants.CacheConstants;
import com.jfeng.pan.core.exception.RPanBusinessException;
import com.jfeng.pan.core.response.ResponseCode;
import com.jfeng.pan.core.utils.IdUtil;
import com.jfeng.pan.core.utils.JwtUtil;
import com.jfeng.pan.core.utils.PasswordUtil;
import com.jfeng.pan.server.common.cache.AnnotationCacheService;
import com.jfeng.pan.server.modules.file.constants.FileConstants;
import com.jfeng.pan.server.modules.file.context.CreateFolderContext;
import com.jfeng.pan.server.modules.file.entity.RPanUserFile;
import com.jfeng.pan.server.modules.file.service.IUserFileService;
import com.jfeng.pan.server.modules.user.constants.UserConstants;
import com.jfeng.pan.server.modules.user.context.*;
import com.jfeng.pan.server.modules.user.converter.UserConverter;
import com.jfeng.pan.server.modules.user.entity.RPanUser;
import com.jfeng.pan.server.modules.user.service.IUserService;
import com.jfeng.pan.server.modules.user.mapper.RPanUserMapper;
import com.jfeng.pan.server.modules.user.vo.UserInfoVO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.Collection;
import java.util.Date;
import java.util.List;
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

    @Autowired
    @Qualifier(value = "userAnnotationCacheService")
    private AnnotationCacheService<RPanUser> cacheService;

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

    /**
     * <p>
     * 用户忘记密码——校验用户名称并返回密保问题
     * 查询数据库的Username
     * </p>

     * @param checkUsernameContext 校验上下文
     * @return 返回密保问题
     */
    @Override
    public String checkUsername(CheckUsernameContext checkUsernameContext) {
        String question = baseMapper.selectQuestionByUsername(checkUsernameContext.getUsername());
        if(StringUtils.isEmpty(question)){
            throw new RPanBusinessException("没有此用户");
        }
        return question;
    }

    /**
     * 用户忘记密码——校验密保答案并生成登录token
     * @param checkAnswerContext 校验密保问题上下文
     * @return
     */
    @Override
    public String checkAnswer(CheckAnswerContext checkAnswerContext) {
        LambdaQueryWrapper<RPanUser> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(RPanUser::getUsername, checkAnswerContext.getUsername())
                .eq(RPanUser::getQuestion, checkAnswerContext.getQuestion())
                .eq(RPanUser::getAnswer, checkAnswerContext.getAnswer());
        long count = count(queryWrapper);
        if(count == 0){
            throw new RPanBusinessException("密保答案错误");
        }
        return generateAccessToken(checkAnswerContext);
    }

    /**
     * 用户重置密码
     * 1、校验token是否有效
     * 2、重置密码
     * @param resetPasswordContext 重置密码上下文
     */
    @Override
    public void resetPassword(ResetPasswordContext resetPasswordContext) {
        checkForgetPasswordToken(resetPasswordContext);
        checkAndResetUserPassword(resetPasswordContext);
    }

    /**
     * 用户在线更新密码
     * 1、校验旧密码
     * 2、重置新密码
     * 3、退出当前的登录状态
     * @param changePasswordContext 更新密码上下文
     */
    @Override
    public void changePassword(ChangePasswordContext changePasswordContext) {
        checkOldPassword(changePasswordContext);
        doChangePassword(changePasswordContext);
        exitLoginStatus(changePasswordContext);
    }

    /**
     * 在线查询用户的基本信息
     * @param userId 用户唯一标识
     * @return
     */
    @Override
    public UserInfoVO info(Long userId){
        RPanUser entity = getById(userId);
        if(Objects.isNull(entity)){
            throw new RPanBusinessException("用户查询信息失败");
        }

        RPanUserFile rPanUserFile = getUserRootFileInfo(userId);
        if(Objects.isNull(rPanUserFile)){
            throw new RPanBusinessException("查询用户根文件夹信息失败");
        }
        return userConverter.assembleUserInfoVO(entity, rPanUserFile);
    }

    /**
     * 根据ID查询
     * @param id 序列化ID
     * @return
     */
    @Override
    public RPanUser getById(Serializable id) {
        return cacheService.getById(id);
//        return super.getById(id);
    }

    /**
     * 根据ID更新
     *
     * @param entity
     * @return
     */
    @Override
    public boolean updateById(RPanUser entity) {
        return cacheService.updateById(entity.getUserId(),entity);
//        return super.updateById(entity);
    }

    /**
     * 根据ID删除并清空cache信息
     * 委托注入的UserChacheService的mapper进行deleteById
     *
     * @param entity 实体信息
     * @return
     */
    @Override
    public boolean removeById(RPanUser entity) {
        return cacheService.removeById(entity.getUserId());
//        return super.removeById(entity);
    }

    /**
     * 根据ID批量查询
     *
     * @param idList 序列化ID列表
     * @return
     */
    @Override
    public List<RPanUser> listByIds(Collection<? extends Serializable> idList) {
        throw new RPanBusinessException("请更换手动缓存处理");
//        return super.listByIds(idList);
    }

    /**
     * 根据ID批量更新
     * @param entityList 更新的实体信息
     * @return
     */
    @Override
    public boolean updateBatchById(Collection<RPanUser> entityList) {
        throw new RPanBusinessException("请更换手动缓存处理");
//        return super.updateBatchById(entityList);
    }

    /**
     * UserID批量删除
     * 清空cache信息，但使用注解开发不支持该操作
     *
     * @param list 主键ID列表
     * @return
     */
    @Override
    public boolean removeByIds(Collection<?> list) {
        throw new RPanBusinessException("请更换手动缓存处理");
//        return super.removeByIds(list, useFill);
    }

    /****************************************************** private ***************************************************************/

    /**
     * 获取用户根目录夹信息实体
     *
     * @param userId 用户唯一标识
     * @return
     */
    private RPanUserFile getUserRootFileInfo(Long userId) {
        return iUserFileService.getUserRootFile(userId);
    }

    /**
     * 退出用户的登录状态
     * @param changePasswordContext
     */
    private void exitLoginStatus(ChangePasswordContext changePasswordContext) {
        exit(changePasswordContext.getUserId());
    }

    /**
     * 修改新密码信息
     * @param changePasswordContext
     */
    private void doChangePassword(ChangePasswordContext changePasswordContext) {
        String newPassword = changePasswordContext.getNewPassword();
        RPanUser entity = changePasswordContext.getEntity();
        String salt = entity.getSalt();
        String encNewPassword = PasswordUtil.encryptPassword(salt, newPassword);

        entity.setPassword(encNewPassword);
        if(!updateById(entity)){
            throw new RPanBusinessException("修改用户密码失败");
        }
    }

    /**
     * 校验用户旧密码
     * 该代码查询并封装用户实体信息到上下文对象中
     * @param changePasswordContext
     */
    private void checkOldPassword(ChangePasswordContext changePasswordContext) {
        Long userId = changePasswordContext.getUserId();
        String oldPassword = changePasswordContext.getOldPassword();

        RPanUser entity = getById(userId);
        if(Objects.isNull(entity)){
            throw new RPanBusinessException("用户信息不存在");
        }
        changePasswordContext.setEntity(entity);
        String encOldPassword = PasswordUtil.encryptPassword(entity.getSalt(), oldPassword);
        if(!encOldPassword.equals(entity.getPassword())){
            throw new RPanBusinessException("旧密码不正确");
        }
    }


    /**
     * 校验并重置密码
     * @param resetPasswordContext 重置密码上下文
     */
    private void checkAndResetUserPassword(ResetPasswordContext resetPasswordContext) {
        String username = resetPasswordContext.getUsername();
        String password = resetPasswordContext.getPassword();
        RPanUser entity = getRPanUserByUsername(username);
        if(Objects.isNull(entity)){
            throw new RPanBusinessException("用户信息不存在");
        }
        String newDbPassword = PasswordUtil.encryptPassword(entity.getSalt(), password);
        entity.setPassword(newDbPassword);
        entity.setUpdateTime(new Date());

        if( !updateById(entity)){
            throw new RPanBusinessException("重置用户密码失败");
        }


    }

    /**
     * 验证忘记密码的token信息是否有效
     * @param resetPasswordContext 重置密码上下文信息
     */
    private void checkForgetPasswordToken(ResetPasswordContext resetPasswordContext) {
        Object value = JwtUtil.analyzeToken(resetPasswordContext.getToken(), UserConstants.FORGET_USERNAME);
        if(Objects.isNull(value)){
            throw new RPanBusinessException(ResponseCode.TOKEN_EXPIRE);
        }
        String tokenUsername = String.valueOf(value);
        if(!resetPasswordContext.getUsername().equals(tokenUsername)){
            throw new RPanBusinessException("token信息与用户信息不一致");
        }
    }


    /**
     * <p>
     *     用户忘记密码——校验密保通过的临时token
     *     token的失效时间定义为5分钟
     * </p>
     * @param checkAnswerContext
     * @return
     */
    private String generateAccessToken(CheckAnswerContext checkAnswerContext) {
        return JwtUtil.generateToken(checkAnswerContext.getUsername(), UserConstants.FORGET_USERNAME, checkAnswerContext.getUsername(), UserConstants.FIVE_MINUTES_LONG);
    }


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




