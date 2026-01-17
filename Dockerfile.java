# 多阶段构建 Dockerfile for Java
# 第一阶段：构建阶段
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

# 复制 pom.xml 和源代码
COPY pom.xml .
COPY src ./src

# 构建应用
RUN mvn clean package -DskipTests

# 第二阶段：运行阶段
FROM eclipse-temurin:17-jre-jammy

# 安装必要的运行时依赖
RUN apt-get update && \
    apt-get install -y --no-install-recommends wget sqlite3 && \
    rm -rf /var/lib/apt/lists/*

# 创建应用用户
RUN groupadd -r appgroup && useradd -r -g appgroup appuser

WORKDIR /app

# 从构建阶段复制 JAR 文件
COPY --from=builder /app/target/*.jar app.jar

# 复制前端静态文件目录
COPY --from=builder /app/src/main/resources/static /app/web

# 创建数据目录
RUN mkdir -p /app/data && chown -R appuser:appgroup /app

# 切换到非root用户
USER appuser

# 暴露端口
EXPOSE 8080

# 默认启动Web服务
ENTRYPOINT ["java", "-jar", "app.jar"]
