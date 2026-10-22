package com.recipick.domain.recipe.service;

import com.recipick.domain.recipe.exception.RecipeErrorCode;
import com.recipick.global.exception.BusinessException;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

final class YoutubeUrlParser {

    private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{11}");
    private static final Set<String> YOUTUBE_HOSTS = Set.of("youtube.com", "www.youtube.com", "m.youtube.com");
    private static final String SHORT_HOST = "youtu.be";
    private static final String SHORTS_PATH = "/shorts/";

    private YoutubeUrlParser() {
    }

    static String extractVideoId(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        String videoId = findVideoId(url.trim());
        if (videoId == null || !VIDEO_ID.matcher(videoId).matches()) {
            throw new BusinessException(RecipeErrorCode.INVALID_YOUTUBE_URL);
        }
        return videoId;
    }

    private static String findVideoId(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(RecipeErrorCode.INVALID_YOUTUBE_URL, e);
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        String path = uri.getPath() == null ? "" : uri.getPath();

        if (SHORT_HOST.equals(host)) {
            return path.startsWith("/") ? path.substring(1) : null;
        }
        if (!YOUTUBE_HOSTS.contains(host)) {
            return null;
        }
        if (path.startsWith(SHORTS_PATH)) {
            return path.substring(SHORTS_PATH.length());
        }
        if ("/watch".equals(path)) {
            return findQueryValue(uri.getQuery(), "v").orElse(null);
        }
        return null;
    }

    private static Optional<String> findQueryValue(String query, String name) {
        if (query == null) {
            return Optional.empty();
        }
        return Arrays.stream(query.split("&"))
            .filter(pair -> pair.startsWith(name + "="))
            .map(pair -> pair.substring(name.length() + 1))
            .findFirst();
    }
}
