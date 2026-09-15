# syntax=docker/dockerfile:1
#
# backend のマルチステージビルド。
#   1. build   : Gradle で bootJar を作る(テストは CI で実行済みのためスキップ)
#   2. extract : Spring Boot の layertools でレイヤーに分解し、依存と自作コードを別レイヤーにする
#   3. runtime : JRE のみの alpine イメージで非 root 実行
#
# ベースイメージはダイジェストで固定し、Dependabot(docker)が更新する。

# ---- 1. build --------------------------------------------------------------
FROM eclipse-temurin:25-jdk-alpine@sha256:09349d79941fd53bb3d487b393ca118d8853c08c09193f416fe6a8718df9e732 AS build
WORKDIR /workspace

# 依存解決をソース変更から切り離してキャッシュを効かせる
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle/ gradle/
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon dependencies --quiet

COPY src/ src/
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon bootJar -x test --quiet

# ---- 2. extract ------------------------------------------------------------
FROM eclipse-temurin:25-jdk-alpine@sha256:09349d79941fd53bb3d487b393ca118d8853c08c09193f416fe6a8718df9e732 AS extract
WORKDIR /workspace
COPY --from=build /workspace/build/libs/app.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

# ---- 3. runtime ------------------------------------------------------------
FROM eclipse-temurin:25-jre-alpine@sha256:3137541deb3cac6626b5d9a4a2187bc0d6a34312f858bd2c67dd01e732e6b682 AS runtime

# 非 root ユーザー(固定 UID/GID で再現性を確保)
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app -h /app app
WORKDIR /app

# 変更頻度の低いレイヤーから順にコピーする
COPY --from=extract --chown=app:app /workspace/extracted/dependencies/ ./
COPY --from=extract --chown=app:app /workspace/extracted/spring-boot-loader/ ./
COPY --from=extract --chown=app:app /workspace/extracted/snapshot-dependencies/ ./
COPY --from=extract --chown=app:app /workspace/extracted/application/ ./

# 数値指定にして runAsNonRoot 系のチェックで解決可能にする
USER 10001:10001

# PORT は Cloud Run が注入する。MANAGEMENT_PORT は ingress に公開しない(#3 の決定)
ENV PORT=8180 \
    MANAGEMENT_PORT=8181 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8180

# Cloud Run は HEALTHCHECK を無視する(probe は Terraform 側で定義)。ローカル compose 用
HEALTHCHECK --interval=10s --timeout=3s --start-period=20s --retries=3 \
    CMD ["sh", "-c", "wget -q -O /dev/null http://127.0.0.1:${MANAGEMENT_PORT}/actuator/health/readiness"]

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
