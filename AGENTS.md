# AGENT Instructions

# Project Guidelines
- Agents are allowed to suggest new features, but they will be implemented by humans.
- Agents will write unit tests for new features
- Always ask before running commands.
- Do not assume information you do not have. Always ask before implementing a change.
- Agents are supposed to help the developer accomplish tasks more efficiently
## Coding Standards
- Follow existing code style in the module being edited
- Use meaningful variable and method names
- Keep methods short and focused on a single responsibility
## Testing
- Write unit tests for all new business logic
- Run tests before submitting changes
- Do not bypass or weaken failing tests
## Microservices Architecture
  - The application is divided into 4 modules:
       - product (`product`): Handles product catalog CRUD operations, backed by **PostgreSQL** for persistent storage and **Redis** for high-performance caching.
       - Auth (`auth`): Identity management, registration, and JWT token issuance. 
       - Api gateway (`api-gateway`)**: Routing, Global JWT Filter, Rate limiter. 
       - Order (`order`) Feign client, asynchronous messaging (Kafka), Transaction management.
  - Each service (auth, order, product) should be independent
  - API Gateway handles routing and cross-cutting concerns
  - Use RESTful conventions for inter-service communication
  - When the whole application is finished it will be dockerized 
## Commits
- Do not commit, just make changes which I will review and then commit myself
## Response Language
- Communicate in English