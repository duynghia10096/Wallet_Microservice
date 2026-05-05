# 🚀 Wallet Service - Startup Guide

## 📌 Nhanh chóng - Cách Start Wallet Service

### ✅ File chính để start:
```
services/wallet-service/src/main/java/com/wallet/wallet/WalletServiceApplication.java
```

### 1️⃣ **Terminal - Chạy Local (Cách Dễ Nhất)**

```bash
# Điều hướng tới wallet-service
cd services/wallet-service

# Chạy Spring Boot
mvn spring-boot:run
```

**Kết quả**: Service sẽ start trên **http://localhost:8082**

### 2️⃣ **Hoặc Build + Run**

```bash
# Build project
mvn clean install -DskipTests

# Chạy service
mvn spring-boot:run
```

### 3️⃣ **Hoặc Build JAR rồi chạy**

```bash
# Build
mvn clean package -DskipTests

# Chạy JAR file
java -jar target/wallet-service-1.0.0.jar
```

---

## 🔌 Test Wallet Service

### Kiểm tra health check
```bash
curl http://localhost:8082/api/v1/wallets/health
```

**Response**: `"Wallet service is running"`

### Lấy chi tiết ví
```bash
curl http://localhost:8082/api/v1/wallets/{walletId}
```

### Trừ tiền (Debit)
```bash
curl -X POST http://localhost:8082/api/v1/wallets/{walletId}/debit \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 100000,
    "currency": "VND"
  }'
```

### Cộng tiền (Credit)
```bash
curl -X POST http://localhost:8082/api/v1/wallets/{walletId}/credit \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 100000,
    "currency": "VND"
  }'
```

### Kiểm tra số dư (Balance)
```bash
curl http://localhost:8082/api/v1/wallets/{walletId}/balance
```

---

## 🗂️ File quan trọng

| File | Mục đích |
|------|---------|
| **WalletServiceApplication.java** | 🔴 **ENTRY POINT** (file chính) |
| **WalletController.java** | REST API endpoints |
| **WalletApplicationService.java** | Business logic |
| **Wallet.java** | Domain model |
| **application.yml** | Configuration |

---

## 📋 Prerequisites (Yêu cầu)

- ✅ Java 21+
- ✅ Maven 3.8+
- ✅ PostgreSQL running (hoặc update DB config)
- ✅ Kafka running (cho event streaming)
- ✅ Eureka running (cho service discovery)

---

## ⚠️ Nếu lỗi:

### Lỗi: "Cannot find WalletServiceApplication"
**Giải pháp**: File đã được tạo ở:
```
services/wallet-service/src/main/java/com/wallet/wallet/WalletServiceApplication.java
```

### Lỗi: Database connection
**Giải pháp**: Update `application.yml` với DB credentials

### Lỗi: Kafka connection
**Giải pháp**: Đảm bảo Kafka chạy trên `localhost:9092`

### Lỗi: Port already in use
**Giải pháp**: 
```bash
# Thay đổi port trong application.yml
server:
  port: 8083  # Thay vì 8082
```

---

## 🎯 Architecture

```
Wallet Service (8082)
    ├── WalletServiceApplication.java  ← Main entry point
    ├── WalletController.java          ← REST endpoints
    ├── WalletApplicationService.java  ← Business logic
    ├── Wallet.java                    ← Domain model
    └── application.yml                ← Configuration
```

---

## 📊 Microservices Stack to Start

Nếu muốn start tất cả services:

```bash
# Terminal 1: Eureka (Service Discovery)
cd infrastructure/eureka-server && mvn spring-boot:run

# Terminal 2: API Gateway
cd infrastructure/api-gateway && mvn spring-boot:run

# Terminal 3: Payment Service
cd services/payment-service && mvn spring-boot:run

# Terminal 4: Wallet Service  ← BẠN ĐANG CẬP NHẬT
cd services/wallet-service && mvn spring-boot:run

# Terminal 5: Transaction Service
cd services/transaction-service && mvn spring-boot:run

# Terminal 6: Notification Service
cd services/notification-service && mvn spring-boot:run

# Terminal 7: User Service
cd services/user-service && mvn spring-boot:run
```

---

## 🌐 Access Wallet Service

- **Service URL**: `http://localhost:8082`
- **Health Check**: `http://localhost:8082/api/v1/wallets/health`
- **Eureka Registration**: Tự động đăng ký lên Eureka

---

**✅ Wallet Service sẵn sàng start!** 🚀
