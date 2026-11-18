# Architecture Overview

This project implements a distributed microservices architecture designed for high scalability, separation of concerns, and resilience.

## Core Architectural Patterns
- **Database-per-Service**: Each microservice maintains its own dedicated PostgreSQL database, preventing data coupling across domain boundaries.
- **API Gateway Pattern**: Single entry point handling client requests, routing, cross-cutting concerns (CORS, JWT validation), and rate limiting.
- **Inter-Service Security**: Internal communication is secured via shared cryptographic tokens (`INTERNAL_SECRET`).
- **Real-Time Communication**: WebSocket with STOMP protocol powers instant messaging and group discussions.
- **Asynchronous & Scheduled Tasks**: Managed through Spring scheduling and event-driven patterns.

## Microservices Breakdown
| Microservice | Port | Database | Primary Responsibility |
|--------------|------|----------|------------------------|
| `api-gateway` | 8080 | - | Central routing and perimeter security |
| `auth-service` | 8081 | `auth_db` | User identity, authentication (JWT), RBAC, profile KYC |
| `content-service` | 8082 | `content_db` | News, media, sports events, press releases |
| `payment-service` | 8083 | `payment_db` | Invoicing, payment ledger, transaction records |
| `shop-service` | 8084 | `shop_db` | Official e-commerce catalog, carts, orders |
| `ticket-service` | 8085 | `ticket_db` | Match ticketing, VIP reservations, subscriptions |
| `notification-service` | 8086 | `notification_db` | Real-time push & in-app user notifications |
| `sports-service` | 8087 | `sports_db` | Teams, rosters, match convocations, match sheets |
| `gamification-service` | 8088 | `wydad_gamification` | Loyalty points, fan ranking, reward badges |
| `election-service` | 8089 | `elections_db` | Club presidency & board member democratic ballots |
| `communication-service` | 8090 | `communication_db` | Real-time team chats and group messaging |
| `wydad-frontend` | 4200 | - | Responsive web client (Angular 19, Tailwind CSS) |
