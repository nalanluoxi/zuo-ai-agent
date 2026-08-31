# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

`zuo-ai-agent` is a Spring Boot 3.4 scaffold project (Java 17) intended as the starting point for an AI agent application. It is currently in early initialization — only a health-check controller and the application entry point exist.

## Common Commands

```bash
# Build
mvn clean package -DskipTests

# Run (port 8123, context path /api)
mvn spring-boot:run

# Test
mvn test

# Run a single test
mvn test -Dtest=ZuoAiAgentApplicationTests
```

## 启动脚本

项目提供三个启动脚本：

- **`./start.sh`** - 主控脚本，支持 start/stop/restart/status，调用后端和前端脚本
- **`./start-backend.sh`** - 启动所有后端服务（中间件 + 4个Java服务）
- **`./start-frontend.sh`** - 启动所有前端服务（web + monitor-web）

### 快速使用

```bash
# 启动全部服务
./start.sh

# 仅启动后端服务
./start.sh backend

# 仅启动前端服务
./start.sh frontend

# 查看状态
./start.sh status
```

## Service Architecture

| Service | Port | Context Path | Description |
|---------|------|--------------|-------------|
| **gateway-service** | 9000 | - | API 网关，对外暴露唯一入口 |
| **auth-service** | 8100 | /api/auth | 认证服务，用户登录/注册/token 管理 |
| **zuo-ai-agent** | 8123 | /api | 主服务，AI 对话/知识库/文档处理 |
| **log-monitor-service** | 8200 | /api/log | 日志监控服务 |

## Frontend Services

| Service | Port | Description |
|---------|------|-------------|
| **web** | 5173 | 主前端（用户界面） |
| **monitor-web** | 5174 | 监控前端 |

## 访问入口 / 登录主页面

> **每次启动后，访问地址：**
> - 🌐 **登录主页面: http://localhost:5173**
> - 测试账号: `admin` / `123456`

## API & Service

### 对外暴露入口

所有前端请求通过 **Gateway (9000)** 路由到后端服务：

- 🌐 **主前端（登录页面）**: `http://localhost:5173`
- 监控前端: `http://localhost:5174`
- 网关 API: `http://localhost:9000`

### 内部服务（不直接对外暴露）

- Health check: `GET /api/health` → returns `"ok"`
- Swagger UI: `http://localhost:8123/api/swagger-ui.html`
- API docs: `http://localhost:8123/api/v3/api-docs`
- Knife4j UI (Chinese): enabled by default

## Key Dependencies

| Dependency | Version | Purpose |
|---|---|---|
| Spring Boot | 3.4.4 | Web framework |
| Lombok | 1.18.36 | Boilerplate reduction |
| Hutool | 5.8.37 | Utility library |
| Knife4j (OpenAPI 3) | 4.4.0 | API documentation UI |

## Project Structure

```
src/main/java/com/example/zuoaiagent/
  ZuoAiAgentApplication.java       # Entry point
  controller/TestController.java   # Health check endpoint
src/main/resources/
  application.yaml                 # Port 8123, context /api, Knife4j config
src/test/
  ZuoAiAgentApplicationTests.java  # Spring context load test
```

## Notes

- The `springdoc` config in `application.yaml` references `com.yupi.zuoaiagent.controller` (mismatched package name) — the actual controller package is `com.example.zuoaiagent.controller`. Fix this if adding more controllers and expecting them to appear in Swagger.