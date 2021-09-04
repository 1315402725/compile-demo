package com.demo.products.util;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;
import java.util.logging.Logger;

@Component
public class Global {

    private static Environment env;
    @Autowired
    private Environment environment;

    @PostConstruct
    public void init() {
        env = this.environment;
    }

    /**
     * 模拟读取远端配置
     */
    public static String getConfig(String key) {
        return env.getProperty(key);
    }
}
