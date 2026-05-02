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

## API & Service

- Base URL: `http://localhost:8123/api`
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