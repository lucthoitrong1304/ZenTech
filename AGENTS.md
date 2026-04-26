# 🧠 Backend Rules - Spring Boot Ecommerce

## 1. Project Overview
- Backend system for ecommerce
- Architecture: RESTful API
- Pattern: Layered (NO domain layer)
- Tech: Java Spring Boot

---

## 2. Architecture

Flow:

Request
→ Controller (validate input)
→ Service (business logic)
→ Repository (DB)
→ Service
→ Mapper
→ Response DTO
→ Client

---

## 3. Package Structure

- config: general system config (CORS, Jackson, Swagger, DB)
- controller: handle HTTP request/response
- dto: request/response objects
- exception: global exception handler
- init: seed data (ignore)
- mapper: convert Entity ↔ DTO
- model: contains all Entities and Enums
- repository: database access layer
  - repository/projection: interface-based projections for optimized queries
- security: authentication & authorization (JWT, filter, security config)
- service: business logic layer

---

## 4. Core Principles

- Service = ONLY place for business logic
- Controller = NO business logic
- Repository = NO business logic
- Entity = NO business logic
- Mapper = NO business logic

---

## 5. Controller Rules

- Use @Valid for request validation
- Only:
  - receive request
  - validate input
  - call service
  - return response

Forbidden:
- No business logic
- No repository access

---

## 6. Service Rules

- Contains ALL business logic
- Can:
  - call repository
  - call other services

Must:
- Return DTO (NOT Entity)

---

## 7. Repository Rules

- Only interact with database
- No business logic

---

## 8. Projection Rules

- Use Interface-based Projections for specific/aggregated queries
- Location: MUST be placed inside `repository/projection`
- Do NOT place in `dto` or `model` packages
- Ensure JPQL/Native query aliases exactly match interface getter methods
- Forbidden: No business logic

---

## 9. Model Rules

- Contains:
  - JPA Entities
  - Enums

Must NOT:
- contain business logic
- call service or repository

Naming:
- Entity: User, Order, Product
- Enum: OrderStatus, Role

---

## 10. Mapper Rules

- Convert:
  - DTO → Entity
  - Entity → DTO

Forbidden:
- No business logic
- No DB access

---

## 11. Validation Rules

- Controller: validate request format (@Valid)
- Service: validate business logic

---

## 12. Security Rules

- All auth logic MUST be in security package
- Includes:
  - SecurityConfig
  - JWT handling
  - Filters
  - UserDetailsService

- DO NOT place security logic in config

---

## 13. Config Rules

- Only general configuration:
  - CORS
  - Jackson
  - Swagger
  - Database

---

## 14. Error Handling

- Use global exception handler
- Standard error response:

{
"timestamp": "...",
"status": 400,
"message": "...",
"errors": []
}

---

## 15. Response Rules

- Always return DTO
- Never return Entity directly

Recommended format:

{
"success": true,
"data": {},
"message": "OK"
}

---

## 16. Coding Rules

- Use constructor injection only
- Keep methods small (single responsibility)
- Do NOT modify unrelated code
- Follow existing patterns in project

---

## 17. AI Agent Workflow (VERY IMPORTANT)

Before coding:
- Understand requirement
- Identify affected layers
- Follow architecture strictly

During coding:
- Make minimal changes
- Follow existing patterns

After coding:
- Ensure:
  - correct layer usage
  - no business logic leakage
  - consistent structure
  - proper use of projections vs DTOs

---

## 18. Constraints

- DO NOT refactor unrelated code
- DO NOT introduce new architecture
- DO NOT change API contract unless required