package com.jfeng.pan.server;

import com.jfeng.pan.core.constants.RPanConstants;
import com.jfeng.pan.server.common.stream.channel.PanChannel;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.EnableTransactionManagement;



@SpringBootApplication(scanBasePackages = RPanConstants.BASE_COMPONENT_SCAN_PATH )
@ServletComponentScan(basePackages = RPanConstants.BASE_COMPONENT_SCAN_PATH)
@EnableTransactionManagement
@MapperScan(basePackages =  RPanConstants.BASE_COMPONENT_SCAN_PATH+".server.modules.**.mapper")
@EnableAsync
@EnableBinding(PanChannel.class)
public class RPanServerLauncher {
    public static void main(String[] args) {
        SpringApplication.run(RPanServerLauncher.class, args);
    }


}
