package com.recipick.global.config;

import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.io.IOException;
import java.nio.file.Path;
import javax.crypto.SecretKey;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    @Bean
    public SecretKey jwtSigningKey(JwtProperties properties, Environment environment) throws IOException {
        if (properties.secret() != null && !properties.secret().isBlank()) {
            return Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.secret()));
        }
        String[] profiles = environment.getActiveProfiles();
        if (profiles.length == 0) {
            profiles = environment.getDefaultProfiles();
        }
        if (profiles.length != 1 || !"local".equals(profiles[0])) {
            throw new IllegalStateException("로컬 외 환경에서는 JWT_SECRET 설정이 필요합니다.");
        }
        Path keyPath = Path.of(environment.getProperty("security.jwt.local-key-path",
                Path.of(System.getProperty("user.home"), ".recipick", "jwt-secret").toString()));
        return new LocalJwtKeyStore().loadOrCreate(keyPath);
    }
}
