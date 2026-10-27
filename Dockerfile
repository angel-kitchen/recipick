# CI가 만든 실행 jar를 담는 운영 이미지. 빌드는 Gradle 캐시가 있는 CI에서 하고 서버에서는 빌드하지 않는다.
# 로컬 검증: ./gradlew bootJar -x test && docker build -t recipick:local .
FROM eclipse-temurin:17-jre

# 컨테이너 안에서 root로 실행하지 않는다
RUN useradd --system --uid 1001 app
WORKDIR /app

# 일반 jar(-plain)가 함께 생기므로 실행 가능한 jar만 복사한다
COPY --chown=app:app build/libs/*-SNAPSHOT.jar app.jar

# JVM 기본 시간대는 TZ만으로 보장되지 않으므로 명시한다(LocalDateTime.now()가 한국 시간이어야 한다).
# 힙 상한은 compose 메모리 제한의 75%로 두고, 필요하면 compose에서 JAVA_TOOL_OPTIONS를 덮어쓴다
ENV TZ=Asia/Seoul \
    JAVA_TOOL_OPTIONS="-Duser.timezone=Asia/Seoul -XX:MaxRAMPercentage=75.0"

USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
