package com.jfeng.pan.storage.engine.rustfs.config;

import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class LuaScriptConfig {

    private static final String LUA_CHECK_AND_PUT_PATH = "lua/check_and_put.lua";

    private static final String LUA_GET_IF_EXISTS_PATH = "lua/get_if_exists.lua";

    @Bean
    public DefaultRedisScript<String> checkAndPutRedisScript() throws IOException {
        ClassPathResource resource = new ClassPathResource(LUA_CHECK_AND_PUT_PATH);
        String script = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        DefaultRedisScript<String> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(script);
        redisScript.setResultType(String.class);
        return redisScript;
    }

    @Bean
    public DefaultRedisScript<String> getIfExistsRedisScript() throws IOException {
        ClassPathResource resource = new ClassPathResource(LUA_GET_IF_EXISTS_PATH);
        String script = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        DefaultRedisScript<String> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(script);
        redisScript.setResultType(String.class);
        return redisScript;
    }
}
