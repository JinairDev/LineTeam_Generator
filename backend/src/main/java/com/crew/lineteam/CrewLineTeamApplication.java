package com.crew.lineteam;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CrewLineTeamApplication {

    public static void main(String[] args) {
        // Java 21+ (JEP 452): JDK 기본 cacerts 외에 OS 신뢰 저장소(맥 키체인 등) 사용.
        // SASE/SSL 검사 환경에서 PKIX 오류를 줄이기 위해, 명시적으로 끄지 않은 경우에만 켬.
        if (System.getProperty("jdk.tls.client.useSystemTrustStore") == null
                && Runtime.version().feature() >= 21) {
            System.setProperty("jdk.tls.client.useSystemTrustStore", "true");
        }
        SpringApplication.run(CrewLineTeamApplication.class, args);
    }
}
