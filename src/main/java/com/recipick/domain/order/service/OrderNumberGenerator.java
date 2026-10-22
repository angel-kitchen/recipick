package com.recipick.domain.order.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderNumberGenerator {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String CHARACTERS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int RANDOM_LENGTH = 10;

    // 순번으로 만들면 주문량이 드러나고 다른 주문번호를 추측할 수 있어 난수를 붙인다
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    public String generate() {
        StringBuilder orderNumber = new StringBuilder(LocalDate.now(clock).format(DATE_FORMAT)).append('-');
        for (int i = 0; i < RANDOM_LENGTH; i++) {
            orderNumber.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }
        return orderNumber.toString();
    }
}
