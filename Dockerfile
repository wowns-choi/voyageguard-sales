# --- 1단계: 빌드 ---
FROM eclipse-temurin:17-jdk AS builder
WORKDIR /app

# gradle 캐시를 활용하기 위해 의존성 관련 파일부터 먼저 복사 (소스코드보다 먼저!)
COPY gradlew .
# Windows CRLF로 인한 셔뱅 깨짐 방지 + 실행권한 부여
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN ./gradlew dependencies --no-daemon || true

# 나머지 소스코드 복사 후 빌드
COPY src src
# gradle-git-properties 플러그인이 커밋 정보를 읽으려면 .git이 빌드 컨텍스트에 있어야 함 -
# 멀티스테이지 빌드라 최종 런타임 이미지엔 안 남음
COPY .git .git
RUN ./gradlew bootJar --no-daemon

# --- 2단계: 런타임 ---
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
