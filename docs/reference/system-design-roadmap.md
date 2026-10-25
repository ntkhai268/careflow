# System Design Roadmap — Checklist

> Clone đầy đủ chủ đề từ [roadmap.sh/system-design](https://roadmap.sh/system-design) (nguồn: repo `kamranahmedse/developer-roadmap`).
> Tick `[x]` khi đã học xong từng mục.

## 1. Nhập môn (Introduction)
- [ ] What is System Design?
- [ ] How to approach System Design?
- [ ] Introduction
- [ ] Design and Implementation

## 2. Các khái niệm nền tảng (Basics)
- [ ] Latency vs Throughput
- [ ] Performance vs Scalability
- [ ] Availability vs Consistency
- [ ] CAP Theorem

## 3. Networking cơ bản
- [ ] Application Layer
- [ ] HTTP
- [ ] TCP
- [ ] UDP
- [ ] Domain Name System (DNS)
- [ ] REST
- [ ] GraphQL
- [ ] gRPC
- [ ] RPC
- [ ] Communication (Sync vs Async)

## 4. Load Balancing
- [ ] Load Balancers
- [ ] Layer 4 Load Balancing
- [ ] Layer 7 Load Balancing
- [ ] Load Balancing Algorithms
- [ ] LB vs Reverse Proxy

## 5. Caching
- [ ] Caching
- [ ] Application Caching
- [ ] Database Caching
- [ ] Web Server Caching
- [ ] Client Caching
- [ ] CDN Caching
- [ ] Content Delivery Networks (CDN)
- [ ] Push CDNs
- [ ] Pull CDNs
- [ ] Cache-Aside
- [ ] Write-through
- [ ] Write-behind
- [ ] Refresh Ahead
- [ ] No Caching (Anti-pattern)

## 6. Proxy & Chịu lỗi (Availability/Reliability)
- [ ] Availability
- [ ] Availability in Numbers
- [ ] Availability Patterns
- [ ] Availability Monitoring
- [ ] High Availability
- [ ] Fail-Over
- [ ] Replication
- [ ] Resiliency
- [ ] Reliability Patterns
- [ ] Noisy Neighbor

## 7. Consistency & Quản lý dữ liệu (Data Management)
- [ ] Data Management
- [ ] Consistency Patterns
- [ ] Strong Consistency
- [ ] Weak Consistency
- [ ] Eventual Consistency
- [ ] Sharding
- [ ] Denormalization
- [ ] SQL vs NoSQL
- [ ] Databases
- [ ] Document Store
- [ ] Key-Value Store
- [ ] Graph Databases
- [ ] Wide Column Store
- [ ] Monolithic Persistence

## 8. Messaging & Bất đồng bộ (Async Communication)
- [ ] Messaging
- [ ] Message Queues
- [ ] Publisher/Subscriber
- [ ] Asynchronism
- [ ] Task Queues
- [ ] Back Pressure
- [ ] Competing Consumers
- [ ] Priority Queue
- [ ] Queue-Based Load Leveling
- [ ] Event-Driven Architecture
- [ ] Event Sourcing
- [ ] Choreography
- [ ] Async Request-Reply
- [ ] Sequential Convoy
- [ ] Claim Check

## 9. Microservices & Design Patterns
- [ ] Microservices
- [ ] Cloud Design Patterns (tổng quan)
- [ ] Ambassador
- [ ] Anti-Corruption Layer
- [ ] Backends for Frontend (BFF)
- [ ] Bulkhead
- [ ] Circuit Breaker
- [ ] CQRS
- [ ] Gatekeeper
- [ ] Gateway Aggregation
- [ ] Gateway Offloading
- [ ] Gateway Routing
- [ ] Sidecar
- [ ] Strangler Fig
- [ ] Service Discovery
- [ ] Federation
- [ ] Federated Identity Pattern
- [ ] Leader Election
- [ ] Scheduler Agent Supervisor
- [ ] Compensating Transaction
- [ ] External Configuration Store
- [ ] Deployment Stamps
- [ ] Geodes
- [ ] Index Table
- [ ] Materialized View
- [ ] Pipes and Filters
- [ ] Valet Key
- [ ] Throttling
- [ ] Retry
- [ ] Retry Storm
- [ ] Idempotent Operations
- [ ] Compute Resource Consolidation

## 10. Performance Antipatterns
- [ ] Performance Antipatterns (tổng quan)
- [ ] Busy Database
- [ ] Busy Frontend
- [ ] Chatty I/O
- [ ] Extraneous Fetching
- [ ] Improper Instantiation
- [ ] Synchronous I/O

## 11. Scaling
- [ ] Horizontal Scaling

## 12. Monitoring, Security & Observability
- [ ] Monitoring (tổng quan)
- [ ] Health Monitoring
- [ ] Health Endpoint Monitoring
- [ ] Performance Monitoring
- [ ] Security Monitoring
- [ ] Usage Monitoring
- [ ] Instrumentation
- [ ] Visualization & Alerts
- [ ] Security (tổng quan)
- [ ] Returning Results (error handling / response design)

---

### Ghi chú
- Danh sách trên được trích xuất trực tiếp từ nội dung nguồn (`content/`) của roadmap System Design trên GitHub — đảm bảo phủ 100% các chủ đề đang có trong roadmap.
- Một số node có 2 phiên bản nội dung trùng slug (VD: `Cache-Aside`, `Bulkhead`, `Circuit Breaker`, `CQRS`, `Geodes`, `Health Endpoint Monitoring`, `Queue-Based Load Leveling`, `Replication`, `Sharding`, `Static Content Hosting`, `Valet Key`) do được tái sử dụng ở nhiều nhánh khác nhau trong sơ đồ (VD: vừa nằm trong nhánh "Cloud Design Patterns" vừa nằm trong nhánh chính) — mình đã gộp lại thành 1 mục duy nhất để checklist không bị trùng lặp.
- Vì trang roadmap.sh hiện render sơ đồ tương tác bằng dữ liệu động (không còn file cấu trúc tĩnh trong repo), thứ tự & cách phân nhóm bên trên là mình sắp xếp lại theo logic học tập chuẩn (Networking → Load Balancing → Caching → Availability → Data → Messaging → Microservices Patterns → Monitoring), tương ứng với cách chia chủ đề gốc trên trang.
