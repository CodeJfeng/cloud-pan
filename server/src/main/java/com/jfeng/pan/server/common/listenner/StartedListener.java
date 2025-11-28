package com.jfeng.pan.server.common.listenner;


import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ansi.AnsiColor;
import org.springframework.boot.ansi.AnsiOutput;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/**
 * 项目启动成功日志打印监听器
 */
@Component
@Slf4j
public class StartedListener implements ApplicationListener<ApplicationReadyEvent> {

    /**
     * 项目启动成功将会在日志中输入对应的启动信息
     * @param event 准备就绪的event信息
     */
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        String serverPort = event.getApplicationContext()
                .getEnvironment()
                .getProperty("server.port");
        String serverUrl = String.format("http://%s:%s", "127.0.0.1", serverPort);
        log.info(AnsiOutput.toString(AnsiColor.BRIGHT_BLUE,  "Cloud Drive Server start at "+ serverUrl));
        if(checkShowServerDoc(event.getApplicationContext())){
            log.info(AnsiOutput.toString(AnsiColor.BRIGHT_BLUE,  "Cloud Drive's Doc start at "+ serverUrl+ "/doc.html"));
        }
        log.info(AnsiOutput.toString(AnsiColor.BRIGHT_YELLOW,  "Cloud Drive Server has started successfully!"));
    }

    /**
     * <p>校验是否开启了接口文档
     * <li>springdoc:</li>
     * <li> api-docs:</li>
     * <li>是否开启接口文档</li>
     * <li>enabled: true</li>
     * </p>
     * @param applicationContext
     * @return
     *
     */
    private boolean checkShowServerDoc(ConfigurableApplicationContext applicationContext) {
        return applicationContext.getEnvironment().getProperty("springdoc.api-docs.enabled", Boolean.class, true)
                &&  applicationContext.containsBean("openApiConfig");
    }
}
