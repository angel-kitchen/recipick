package com.recipick.domain.product.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipick.IntegrationTestSupport;
import com.recipick.domain.product.entity.Category;
import com.recipick.domain.product.entity.Mart;
import com.recipick.domain.product.entity.Product;
import com.recipick.domain.product.repository.CategoryRepository;
import com.recipick.domain.product.repository.MartRepository;
import com.recipick.domain.product.repository.ProductRepository;
import com.recipick.global.security.AuthPrincipal;
import com.recipick.global.security.JwtTokenProvider;
import com.recipick.global.security.SubjectType;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

@Transactional
abstract class AdminApiTestSupport extends IntegrationTestSupport {

    protected static final Long SUPER_ADMIN_ID = 1L;
    protected static final Long MART_ADMIN_ID = 2L;

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper mapper;
    @Autowired
    protected JwtTokenProvider tokens;
    @Autowired
    protected MartRepository martRepository;
    @Autowired
    protected CategoryRepository categoryRepository;
    @Autowired
    protected ProductRepository productRepository;

    protected String superToken() {
        return tokens.createTokens(new AuthPrincipal(SubjectType.ADMIN, SUPER_ADMIN_ID, "SUPER_ADMIN", null))
            .accessToken();
    }

    protected String martAdminToken(Long martId) {
        return tokens.createTokens(new AuthPrincipal(SubjectType.ADMIN, MART_ADMIN_ID, "MART_ADMIN", martId))
            .accessToken();
    }

    protected String memberToken() {
        return tokens.createMemberTokens(Long.MAX_VALUE).accessToken();
    }

    protected MockHttpServletResponse get(String url, String token) throws Exception {
        return exchange(MockMvcRequestBuilders.get(url), token, null);
    }

    protected MockHttpServletResponse post(String url, String token, Object body) throws Exception {
        return exchange(MockMvcRequestBuilders.post(url), token, body);
    }

    protected MockHttpServletResponse patch(String url, String token, Object body) throws Exception {
        return exchange(MockMvcRequestBuilders.patch(url), token, body);
    }

    protected MockHttpServletResponse delete(String url, String token) throws Exception {
        return exchange(MockMvcRequestBuilders.delete(url), token, null);
    }

    private MockHttpServletResponse exchange(MockHttpServletRequestBuilder builder, String token, Object body)
        throws Exception {
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
        }
        return mockMvc.perform(builder).andReturn().getResponse();
    }

    protected JsonNode body(MockHttpServletResponse response) throws Exception {
        return mapper.readTree(response.getContentAsString(StandardCharsets.UTF_8));
    }

    protected JsonNode data(MockHttpServletResponse response) throws Exception {
        return body(response).path("data");
    }

    protected String code(MockHttpServletResponse response) throws Exception {
        return body(response).path("code").asText();
    }

    protected Mart saveMart(Long regionId, String name) {
        return martRepository.save(Mart.builder()
            .regionId(regionId)
            .name(name)
            .latitude(new BigDecimal("37.5000000"))
            .longitude(new BigDecimal("127.0000000"))
            .serviceRadiusM(2000)
            .build());
    }

    protected Category saveCategory(String name) {
        return categoryRepository.save(Category.builder().name(name).build());
    }

    protected Product saveProduct(Mart mart, Category category, String name, int quantity) {
        return productRepository.save(Product.builder()
            .mart(mart)
            .category(category)
            .name(name)
            .price(1000L)
            .totalQuantity(quantity)
            .build());
    }

    protected MockHttpServletResponse put(String url, String token, Object body) throws Exception {
        return exchange(MockMvcRequestBuilders.put(url), token, body);
    }
}

