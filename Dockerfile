# ---------- 前端构建 ----------
FROM node:20-slim AS frontend
WORKDIR /build/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
# vite outDir 指向 ../src/main/resources/static,产物会落到 /build/src/main/resources/static
RUN npm run build

# ---------- 后端构建 ----------
FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /build
# 先只拷 pom.xml 拉依赖,充分利用 Docker 层缓存;源码变动不用重新下载依赖
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY src ./src
COPY --from=frontend /build/src/main/resources/static ./src/main/resources/static
RUN mvn -B -q -DskipTests package

# ---------- 运行 ----------
FROM eclipse-temurin:21-jre
WORKDIR /app
# 固定 UID/GID 1000:宿主机挂载 ./data ./logs 时,和 Linux 常见的首个用户对得上
RUN groupadd -g 1000 shiyi \
    && useradd -u 1000 -g 1000 --create-home --shell /usr/sbin/nologin shiyi \
    && mkdir -p /app/data/uploads /app/data/thumbnails /app/logs \
    && chown -R shiyi:shiyi /app
COPY --from=backend /build/start/shiyi-memo-*.jar /app/app.jar
USER shiyi
EXPOSE 8080
VOLUME ["/app/data", "/app/logs"]
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
