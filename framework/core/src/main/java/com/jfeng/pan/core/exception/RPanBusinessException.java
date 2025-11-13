package com.jfeng.pan.core.exception;

import com.jfeng.pan.core.response.ResponseCode;
import lombok.Data;

/**
 * 自定义全局业务异常类
 */
@Data
public class RPanBusinessException extends  RuntimeException{
    private Integer code;
    private String message;

    public RPanBusinessException(ResponseCode responseCode){
        this.code = responseCode.getCode();
        this.message = responseCode.getDesc();
    }

    public RPanBusinessException(String message) {
        this.code = ResponseCode.ERROR.getCode();
        this.message = message;
    }

    public RPanBusinessException(Integer code, String message){
        this.code = code;
        this.message = message ;
    }


    public RPanBusinessException(){
        this.code = ResponseCode.ERROR.getCode();
        this.message = ResponseCode.ERROR.getDesc();
    }
}
