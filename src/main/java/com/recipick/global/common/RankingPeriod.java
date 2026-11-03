package com.recipick.global.common;

import java.time.LocalDateTime;

public enum RankingPeriod {
    DAILY,
    WEEKLY,
    MONTHLY,
    ALL;

    public LocalDateTime startFrom(LocalDateTime now){
        return switch(this){
            case DAILY -> now.minusDays(1);
            case WEEKLY -> now.minusDays(7);
            case MONTHLY -> now.minusDays(30);
            // 전체 기간도 같은 "from 이후" 쿼리 하나로 처리하려고, 어떤 데이터보다 이른 고정 시각을 쓴다.
            // 서비스 시작 전 날짜라 모든 기록이 포함되고, LocalDateTime.MIN은 MySQL datetime 범위를 벗어나 쓰지 않는다
            case ALL -> LocalDateTime.of(2000, 1,1,0,0);
        };
    }
}
