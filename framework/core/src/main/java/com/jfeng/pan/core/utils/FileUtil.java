package com.jfeng.pan.core.utils;

import com.jfeng.pan.core.constants.RPanConstants;
import org.apache.commons.lang3.StringUtils;

/**
 * 文件相关工具类
 */
public class FileUtil {

    public static String getFileSuffix(String filename){
        if (StringUtils.isBlank(filename) || !filename.contains(RPanConstants.POINT_STR)) {
            return StringUtils.EMPTY;
        }
        return filename.substring(filename.lastIndexOf(RPanConstants.POINT_STR));
    }
}
