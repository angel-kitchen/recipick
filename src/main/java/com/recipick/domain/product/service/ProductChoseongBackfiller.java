package com.recipick.domain.product.service;


import com.recipick.domain.product.entity.Product;
import com.recipick.domain.product.repository.ProductRepository;
import com.recipick.global.common.Choseong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductChoseongBackfiller implements ApplicationRunner {

    private final ProductRepository productRepository;
    private final TransactionTemplate transactionTemplate;

    @Override
    public void run(ApplicationArguments args) {
        int total = 0;
        int updated;
        do {
            Integer count = transactionTemplate.execute(status -> {
                List<Product> batch =
                    productRepository.findTop500ByNameChoseongIsNullAndNameIsNotNullOrderByIdAsc();
                batch.forEach(product ->
                    productRepository.updateNameChoseong(product.getId(), Choseong.of(product.getName())));
                return batch.size();
            });
            updated = count == null ? 0 : count;
            total += updated;
        } while (updated > 0);

        if (total > 0) {
            log.info("상품 초성 backfill 완료: {}건", total);
        }
    }
}
