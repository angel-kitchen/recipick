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
            case ALL -> LocalDateTime.of(2000, 1,1,0,0);
        };
    }
}
