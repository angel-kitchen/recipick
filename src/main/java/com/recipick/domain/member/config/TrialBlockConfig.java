package com.recipick.domain.member.config;

import com.recipick.global.config.LocalJwtKeyStore;

import com.recipick.domain.member.service.TrialPhoneHasher;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.io.IOException;
import java.nio.file.Path;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class TrialBlockConfig {
    @Bean
    public TrialPhoneHasher trialPhoneHasher(Environment environment) throws IOException {
        String secret = environment.getProperty("security.trial-block.secret", "");
        if (!secret.isBlank()) {
            return new TrialPhoneHasher(Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret)));
        }
        String[] profiles = environment.getActiveProfiles();
        if (profiles.length == 0) {
            profiles = environment.getDefaultProfiles();
        }
        if (profiles.length != 1 || !"local".equals(profiles[0])) {
            throw new IllegalStateException("로컬 외 환경에서는 TRIAL_BLOCK_HMAC_SECRET 설정이 필요합니다.");
        }
        Path path = Path.of(environment.getProperty("security.trial-block.local-key-path",
                Path.of(System.getProperty("user.home"), ".recipick", "trial-block-secret").toString()));
        return new TrialPhoneHasher(LocalJwtKeyStore.loadOrCreate(path));
    }
}
