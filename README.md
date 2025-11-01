# Document Management and Search System

A comprehensive document management system built with microservices architecture, featuring full-text search, OCR processing, and multilingual support (EN, VI, KOR).

## Overview

This system provides a scalable solution for managing, processing, and searching documents with advanced features like content extraction, multilingual search, user collaboration, and asynchronous processing.

## Key Features

### Document Management
- Upload and store documents in multiple formats (PDF, Word, Excel, images, etc.)
- Document versioning and history tracking
- Cloud storage (AWS S3) and local file system support (for development)
- UTF-8 filename support for international characters
- Document metadata and tagging
- Share documents with customizable permissions

### Search Capabilities
- Full-text search across document content
- Multilingual search support (including Korean via analysis-nori plugin)
- Language-aware search results with automatic detection
- Advanced filtering by type, date, and tags
- Search query history and recommendations

### User Interactions
- Favorites and bookmarking
- Comments and annotations
- Personal notes on documents
- Content recommendations based on viewing history
- Document and comment reporting system

### Security
- JWT-based stateless authentication
- Google OAuth 2.0 integration
- Two-factor authentication (TOTP)
- Email OTP verification
- Password reset with secure tokens
- Role-based access control (RBAC)

### Processing
- Background document processing via RabbitMQ
- Content extraction using Apache Tika
- OCR for scanned documents via Tesseract
- Automatic document indexing to OpenSearch
- Thumbnail generation
- Email notifications for user activities

## Architecture

### Microservices

```
├── eureka-discovery-server (8081)  # Service discovery
├── api-gateway (8086)              # Central API entry point
├── auth-service (8082)             # Authentication & authorization
├── document-interaction-service (8085)  # Document management
├── document-search-service (8083)  # Search functionality
├── processor-service (8084)        # Background processing
└── frontend                        # React SPA
```

### Service Communication Flow

```
Client (React)
    ↓
API Gateway (8086)
    ↓
    ├─→ Auth Service (8082)
    │   └─→ User management, JWT validation
    │
    ├─→ Document Interaction Service (8085)
    │   ├─→ Feign → Auth Service
    │   └─→ RabbitMQ → Processor Service
    │
    ├─→ Document Search Service (8083)
    │   ├─→ Feign → Auth Service
    │   └─→ OpenSearch queries
    │
    └─→ Processor Service (8084) [Background]
        ├─→ RabbitMQ consumers
        ├─→ Content extraction (Tika/OCR)
        ├─→ OpenSearch indexing
        └─→ Email notifications
```

## Technology Stack

### Backend
- **Language:** Java 17
- **Framework:** Spring Boot 3.2.8, Spring Cloud 2023.0.0
- **Build Tool:** Maven
- **Service Discovery:** Netflix Eureka
- **API Gateway:** Spring Cloud Gateway
- **Service Communication:** Spring Cloud OpenFeign

### Frontend
- **Framework:** React 18.3.1
- **Language:** TypeScript 5.6.2
- **Build Tool:** Vite 6.0.1
- **UI Components:** Radix UI, Tailwind CSS 3.4.16
- **State Management:** Redux Toolkit 2.5.1
- **Document Viewing:** react-pdf 9.2.1
- **Form Handling:** React Hook Form with Zod validation
- **Internationalization:** i18next

### Databases & Storage
- **PostgreSQL:** User data, authentication, document metadata
- **MongoDB:** Flexible document-related data
- **OpenSearch 2.17.0:** Full-text search engine with Korean language plugins
- **AWS S3:** Cloud object storage for documents
- **RabbitMQ:** Message queue for async processing

### Document Processing
- **Apache Tika 2.9.1:** Content extraction and language detection
- **Apache PDFBox 2.0.29:** PDF processing
- **Tesseract OCR 5.8.0:** Optical character recognition
- **Lingua:** Language detection library

### Security
- **Spring Security:** Core authentication framework
- **JJWT 0.12.6:** JWT token handling
- **OAuth 2.0:** Google OAuth integration

## Prerequisites

- **Docker** and **Docker Compose** (for containerized deployment)
- **Java 17** (for local development)
- **Node.js 18+** and **npm** (for frontend development)
- **PostgreSQL 14+** (if running locally)
- **MongoDB 6+** (if running locally)
- **OpenSearch 2.17.0** (if running locally)
- **RabbitMQ 3.12+** (if running locally)

## Installation & Setup

### Using Docker Compose (Recommended)

1. Clone the repository:
```bash
git clone https://github.com/loidinhm31/document-management-and-search-system
cd document-management-and-search-system
```

2. Configure environment variables:
Create environment-specific configuration files or set variables in docker-compose.yml:
```bash
# Database
POSTGRES_URL=jdbc:postgresql://postgres:5432/dms
POSTGRES_USERNAME=your_username
POSTGRES_PASSWORD=your_password

# MongoDB
MONGO_URL=mongodb://mongo:27017/dms
MONGO_USERNAME=your_username
MONGO_PASSWORD=your_password

# OpenSearch
OPENSEARCH_URL=http://opensearch:9200
OPENSEARCH_USERNAME=admin
OPENSEARCH_PASSWORD=admin

# RabbitMQ
RABBITMQ_URL=amqp://rabbitmq:5672
RABBITMQ_USERNAME=guest
RABBITMQ_PASSWORD=guest

# AWS S3 (optional)
AWS_S3_BUCKET_NAME=your-bucket-name
AWS_REGION=us-east-1
AWS_ACCESS_KEY=your-access-key
AWS_SECRET_KEY=your-secret-key

# Email (SMTP)
MAIL_SERVER_HOST=smtp.gmail.com
MAIL_SERVER_USERNAME=your-email@gmail.com
MAIL_SERVER_PASSWORD=your-app-password

# OAuth
GOOGLE_CLIENT_ID=your-google-client-id
GOOGLE_CLIENT_SECRET=your-google-client-secret
```

3. Build all services:
```bash
make build-all
```

4. Deploy the stack:
```bash
make deploy-stack
```

5. Deploy frontend:
```bash
make deploy-frontend
```

Or deploy everything at once:
```bash
make deploy-all
```

### Local Development Setup

#### Backend Services

1. Start required infrastructure (PostgreSQL, MongoDB, OpenSearch, RabbitMQ)

2. Navigate to each service directory and run:
```bash
cd <service-name>
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

#### Frontend

1. Navigate to frontend directory:
```bash
cd frontend
```

2. Install dependencies:
```bash
npm install
```

3. Start development server:
```bash
npm run dev
```

## Configuration

Each service supports multiple Spring profiles:
- **local:** Local development environment
- **docker:** Docker containerized environment
- **stage:** Staging environment
- **production:** Production environment

Set the active profile using:
```bash
SPRING_PROFILES_ACTIVE=docker
```

## Usage

### Accessing the Application

- **Frontend:** http://localhost:3000
- **API Gateway:** http://localhost:8086
- **Eureka Dashboard:** http://localhost:8081
- **API Documentation (Swagger):** http://localhost:8086/swagger-ui.html

### API Endpoints

#### Authentication Service (8082)
- `POST /api/v1/auth/register` - User registration
- `POST /api/v1/auth/login` - User login
- `POST /api/v1/auth/verify-otp` - Email verification
- `POST /api/v1/auth/forgot-password` - Request password reset
- `POST /api/v1/auth/reset-password` - Reset password
- `POST /api/v1/auth/enable-2fa` - Enable two-factor authentication

#### Document Interaction Service (8085)
- `POST /api/v1/documents` - Upload document
- `GET /api/v1/documents/{id}` - Get document details
- `PUT /api/v1/documents/{id}` - Update document
- `DELETE /api/v1/documents/{id}` - Delete document
- `POST /api/v1/documents/{id}/favorite` - Add to favorites
- `POST /api/v1/documents/{id}/comments` - Add comment
- `POST /api/v1/documents/{id}/notes` - Add note

#### Document Search Service (8083)
- `GET /api/v1/search` - Full-text search
- `GET /api/v1/search/suggestions` - Search suggestions
- `GET /api/v1/search/history` - User search history

## Database Migrations

Database schema is managed using Flyway migrations located in:
```
auth-service/src/main/resources/db/migration/
```

Migrations run automatically on application startup.

## Project Structure

```
.
├── api-gateway/                    # API Gateway service
├── auth-service/                   # Authentication service
├── document-interaction-service/   # Document management service
├── document-search-service/        # Search service
├── eureka-discovery-server/        # Service discovery
├── processor-service/              # Background processing service
├── frontend/                       # React frontend
│   ├── src/
│   │   ├── components/            # React components
│   │   ├── pages/                 # Page components
│   │   ├── services/              # API services
│   │   ├── store/                 # Redux store
│   │   └── utils/                 # Utility functions
│   └── package.json
└── README.md
```

## Development Commands

### Using Makefile

```bash
# Build all services
make build-all

# Build specific service
make build SERVICE=auth-service

# Build and push to Docker Hub
make build-push-all

# Deploy stack using Docker Swarm
make deploy-stack

# Deploy frontend
make deploy-frontend

# Deploy everything
make deploy-all
```

### Manual Docker Commands

```bash
# Build services
docker-compose -f docker-compose.build.yml build

# Start services
docker-compose up -d

# View logs
docker-compose logs -f <service-name>

# Stop services
docker-compose down
```

## Monitoring & Logging

- **Eureka Dashboard:** Monitor service health and instances at http://localhost:8081
- **Service Logs:** Access via `docker-compose logs -f <service-name>`
- **API Documentation:** Each service exposes Swagger UI for API exploration

## Resource Limits

Services are configured with resource limits for production deployment:

| Service | CPU Limit | CPU Reserved | Memory Limit | Memory Reserved |
|---------|-----------|--------------|--------------|-----------------|
| Eureka | 384M | 192M | - | - |
| Gateway | 384M | 192M | - | - |
| Auth | 384M | 192M | - | - |
| Interaction | 512M | 256M | - | - |
| Search | 512M | 256M | - | - |
| Processor | 2 cores | 1 core | 8G | 4G |

## Troubleshooting

### Services not registering with Eureka
- Ensure Eureka service is running and healthy
- Check `EUREKA_URI` environment variable is correct
- Verify network connectivity between services

### Document processing fails
- Check RabbitMQ is running and accessible
- Verify processor-service has access to `/app/uploads` volume
- Ensure Tesseract OCR is properly installed in processor container

### Search not working
- Verify OpenSearch is running at configured URL
- Check OpenSearch credentials
- Ensure documents have been indexed (check processor-service logs)

### File upload issues
- Check storage configuration (S3 or local path)
- Verify AWS credentials if using S3
- Ensure `/app/uploads` volume is properly mounted

## Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/your-feature`
3. Commit your changes: `git commit -am 'Add new feature'`
4. Push to the branch: `git push origin feature/your-feature`
5. Submit a pull request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Support

For issues and questions, please open an issue in the repository.
