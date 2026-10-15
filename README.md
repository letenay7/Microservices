# E-Commerce Microservices Architecture

A modern, scalable microservices-base backend built with **Java 21**, **Spring Boot 4**, and **Maven**.

## ️ Architecture & Services

* **Product (`product`)**: Handles product catalog CRUD operations, backed by **PostgreSQL** for persistent storage and **Redis** for high-performance caching.
* **Auth (`auth`)**: Identity management, registration, and JWT token issuance.

---

## 🛠️ Tech Stack & Key Technologies

* **Language**: Java 21
* **Framework**: Spring Boot 4.1.1
* **Build Tool**: Apache Maven (Multi-module project)
* **Database**: PostgreSQL 15
* **Cache**: Redis 7 
* **Testing**: JUnit 5, MockMvc, Testcontainers (PostgreSQL & Redis)
* **Containerization**: Docker & Docker Compose

---

## 🚀 Getting Started

### Prerequisites

Ensure you have the following installed locally:
* [JDK 21]
* [Apache Maven 3.9+]
* [Docker Desktop] 

---

Clone this repository
```
git clone https://github.com/letenay7/Microservices.git
```
Please refer to:
* [Dummy init script](dummy_init.sql) for database setup
* [Environment variables](dummy.env)  Required env variables
* [Spring dotenv dependency by Paul Schwarz](https://github.com/paulschwarz/spring-dotenv) to configure Spring Boot env variables

