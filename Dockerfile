# ---------- 构建阶段 ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# 先只拷贝 pom，利用 Docker 层缓存把依赖下载与源码变更解耦
COPY pom.xml .
COPY kit/pom.xml kit/
COPY example/pom.xml example/
RUN mvn -B -ntp -q dependency:go-offline -DskipTests || true

COPY kit/src kit/src
COPY example/src example/src
RUN mvn -B -ntp -DskipTests package

# ---------- 运行阶段 ----------
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 用非 root 用户运行
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /workspace/example/target/example-*.jar /app/app.jar
USER app

EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
