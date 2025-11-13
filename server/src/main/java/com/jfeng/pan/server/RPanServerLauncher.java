package com.jfeng.pan.server;

import com.jfeng.pan.core.constants.RPanConstants;
import com.jfeng.pan.core.response.R;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.constraints.NotBlank;


@SpringBootApplication(scanBasePackages = RPanConstants.BASE_COMPONENT_SCAN_PATH )
@ServletComponentScan(basePackages = RPanConstants.BASE_COMPONENT_SCAN_PATH)
@RestController
@Tag(name = "测试接口类" , description = "程序员测试主程序")
@Validated
@EnableTransactionManagement
@MapperScan(basePackages =  RPanConstants.BASE_COMPONENT_SCAN_PATH+".server.modules.**.mapper")
public class RPanServerLauncher {
    public static void main(String[] args) {
        SpringApplication.run(RPanServerLauncher.class, args);
    }


    /**
     * 打个招呼
     *
     * @param name 你的名字
     * @return 打个招呼
     */
    @GetMapping("hello")
    public R<String> hello(@NotBlank(message = "name不能为空") String name) {
        System.out.println(Thread.currentThread().getContextClassLoader());
        return R.success("Hello " + name+ "!");
    }
}
