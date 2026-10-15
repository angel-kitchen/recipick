package com.recipick;

import com.recipick.global.common.TimeConstants;
import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class RecipickApplication {

    public static void main(String[] args) {
        // LocalDateTime을 한국 시간으로 다루므로 서버 위치와 무관하게 JVM 기본 시간대를 고정한다
        TimeZone.setDefault(TimeZone.getTimeZone(TimeConstants.ZONE_ID));
        SpringApplication.run(RecipickApplication.class, args);
    }

}
