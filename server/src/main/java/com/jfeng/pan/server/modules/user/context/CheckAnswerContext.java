package com.jfeng.pan.server.modules.user.context;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;


/**
 * <p>
 *     <li>用户忘记密码业务--校验密保答案的上下文对象</li>
 *     <li>本实体与PO对象无区别，当业务更改时，更好的维护项目</li>
 * </p>
 */
@Data
public class CheckAnswerContext implements Serializable {
    @Serial
    private static final long serialVersionUID = 445347562342L;
    /**
     * 用户名称
     */
    private String username;

    /**
     * 密保问题
     */
    private String question;

    /**
     * 密保答案
     */
    private String answer;


}
