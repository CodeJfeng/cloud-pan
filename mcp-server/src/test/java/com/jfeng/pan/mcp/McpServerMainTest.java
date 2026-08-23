package com.jfeng.pan.mcp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class McpServerMainTest {

    @Test
    void defaultPortIs10001() {
        assertEquals(10001, PanMcpServerApplication.defaultPort());
    }
}
