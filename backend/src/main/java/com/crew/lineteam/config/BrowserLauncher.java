package com.crew.lineteam.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.net.URI;

/**
 * JAR/EXE 실행 시 서버 기동 후 브라우저를 자동으로 엽니다.
 * (개발 시에는 프론트를 별도 실행하므로 무시해도 됨)
 */
@Component
@Order(1)
public class BrowserLauncher implements ApplicationRunner {

    @Override
    public void run(ApplicationArguments args) {
        if ("false".equalsIgnoreCase(System.getProperty("crew.lineteam.openBrowser", "true"))) return;
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Thread.sleep(2500);
                String port = System.getProperty("server.port", "8765");
                Desktop.getDesktop().browse(URI.create("http://localhost:" + port));
            }
        } catch (Exception ignored) {
        }
    }
}
