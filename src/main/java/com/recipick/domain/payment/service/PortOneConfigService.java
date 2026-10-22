package com.recipick.domain.payment.service;

import com.recipick.domain.payment.dto.response.PortOneConfigResponse;
import com.recipick.domain.payment.exception.PaymentErrorCode;
import com.recipick.global.exception.BusinessException;
import com.recipick.infra.portone.PortOneProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;


@Slf4j
@Service
@RequiredArgsConstructor
public class PortOneConfigService {

    private final PortOneProperties properties;

    public PortOneConfigResponse getPublicConfig() {
        String storeId = properties.storeId();
        String channelKey = properties.channelKey();

        boolean hasStoreId = StringUtils.hasText(storeId);
        boolean hasChannelKey = StringUtils.hasText(channelKey);

        if (!hasStoreId || !hasChannelKey) {
            log.error(
                "PortOne 공개 설정 누락: storeId 있음={}, channelKey 있음={}",
                hasStoreId,
                hasChannelKey
            );
            throw new BusinessException(PaymentErrorCode.PORTONE_CONFIG_ERROR);
        }

        return new PortOneConfigResponse(
            storeId,
            channelKey
        );
    }
}
