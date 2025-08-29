**# CV Shortlisting Application

A Spring Boot application for automated CV/resume processing, shortlisting, and management with advanced duplicate detection and keyword matching capabilities.

## Features

- **Batch CV Processing**: Process multiple CVs simultaneously with configurable batch sizes
- **Intelligent Shortlisting**: Automated CV evaluation based on skills and criteria matching
- **Duplicate Detection**: Advanced similarity scoring to identify duplicate applications
- **Keyword Matching**: Fuzzy matching for skills and qualifications extraction
- **File Organization**: Automatic categorization of processed CVs into organized folder structures
- **RESTful API**: Comprehensive REST endpoints for CV management
- **Security Integration**: Role-based access control (ADMIN, HR roles)
- **Async Processing**: Non-blocking batch operations with configurable thread pools

## Technology Stack

- **Framework**: Spring Boot 3.5.4
- **Java Version**: OpenJDK 23
- **Database**: H2 (in-memory), PostgreSQL support
- **Security**: Spring Security 6.5.2
- **Batch Processing**: Spring Batch 5.2.2
- **Document Processing**: Apache Tika 2.9.1, Apache POI 5.2.4
- **Text Matching**: FuzzyWuzzy 1.4.0
- **Build Tool**: Maven
- **Caching**: Spring Cache abstraction

## Quick Start

### Prerequisites

- Java 23 or higher
- Maven 3.6+
- At least 4GB RAM recommended for batch processing

### Installation

1. **Clone the repository**
```bash
git clone <repository-url>
cd cv-shortlisting
```

2. **Build the application**
```bash
mvn clean install
```

3. **Run the application**
```bash
mvn spring-boot:run
```

The application will start on `http://localhost:8080/cv-tool`

### Directory Structure

Create the following directories in your project root:

```
data/
├── input/          # Place CV files here for processing
├── output/
│   ├── shortlisted/   # Approved CVs
│   ├── others/        # Rejected CVs
│   ├── duplicates/    # Duplicate CVs
│   └── errors/        # Failed processing CVs
```

## API Endpoints

### CV Processing

| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|---------------|
| POST | `/api/v1/cvs/process` | Start batch CV processing | ADMIN/HR |
| GET | `/api/v1/cvs/shortlisted` | Get shortlisted CVs (paginated) | No |
| GET | `/api/v1/cvs/duplicates` | Get duplicate CVs | No |
| GET | `/api/v1/cvs/search` | Search CVs by query and status | No |
| GET | `/api/v1/cvs/{id}` | Get specific CV details | No |
| PUT | `/api/v1/cvs/{id}/status` | Update CV status | ADMIN/HR |
| DELETE | `/api/v1/cvs/{id}` | Delete CV | ADMIN |

### Analytics & Management

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/cvs/stats/duplicates` | Get duplicate statistics |
| GET | `/api/v1/cvs/skills` | Get available skills taxonomy |
| GET | `/api/v1/cvs/config/matching` | Get matching configuration |
| POST | `/api/v1/cvs/reprocess-duplicates` | Reprocess duplicates |

### Monitoring

- **Health Check**: `/actuator/health`
- **Metrics**: `/actuator/metrics`
- **Info**: `/actuator/info`
- **H2 Console**: `/h2-console` (dev only)

## Configuration

### Application Properties

Key configuration options in `application.yml`:

```yaml
spring:
  application:
    name: cv-shortlisting
  
  datasource:
    url: jdbc:h2:mem:cvtool
    driver-class-name: org.h2.Driver
    
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: false
    
  batch:
    job:
      enabled: false  # Jobs triggered via API
      
  security:
    user:
      name: admin
      password: admin123
      roles: ADMIN

# Custom Configuration
cv-processing:
  batch-size: 15
  thread-pool:
    core-size: 6
    max-size: 24
  input-directory: data/input
  output-directory: data/output
```

### Thread Pool Configuration

The application uses multiple thread pools for optimal performance:

- **CV Processing**: 12 core threads, 36 max threads
- **Async Operations**: 6 core threads, 24 max threads
- **Batch Operations**: Dedicated executor for long-running tasks

## Usage Examples

### Processing CVs

```bash
# Start batch processing
curl --location 'http://localhost:8080/cv-tool/api/v1/cvs/process' \
--header 'Content-Type: application/json' \
--header 'Authorization: Basic YWRtaW46Y3Z0b29sMjAyNQ==' \
--data '{
           "inputDirectory": "data/input",
           "batchSize": 15,
            "async": true

       
  
}'
```

### Get Shortlisted CVs

```bash
# Get shortlisted CVs with pagination
curl "http://localhost:8080/cv-tool/api/v1/cvs/shortlisted?page=0&size=10&sortBy=createdDate&sortDir=desc"
```

### Search CVs

```bash
# Search for CVs by skills
curl "http://localhost:8080/cv-tool/api/v1/cvs/search?query=java spring&status=SHORTLISTED"
```

## File Organization

CVs are automatically organized based on processing results:

- **Shortlisted**: `data/output/shortlisted/YYYY-MM-DD/`
- **Rejected**: `data/output/others/YYYY-MM-DD/`
- **Duplicates**: `data/output/duplicates/YYYY-MM-DD/`
- **Errors**: `data/output/errors/YYYY-MM-DD/`

## Supported File Types

- PDF documents (.pdf)
- Microsoft Word (.doc, .docx)
- Rich Text Format (.rtf)
- Plain text files (.txt)
- OpenDocument Text (.odt)

## Logging

The application provides comprehensive logging:

- **Processing Status**: Real-time CV processing updates
- **Error Tracking**: Detailed error messages and stack traces
- **Batch Statistics**: Success rates and processing summaries

Log files are located in the `logs/` directory with daily rotation.

## Caching

Built-in caching for performance optimization:

- **Email Validation**: Cached validation results
- **Fuzzy Matches**: Cached similarity calculations
- **Document Parsing**: Cached parsed content
- **Skill Extraction**: Cached skill matching results
- **Configuration**: Cached system configurations

## Security

### Authentication

Default credentials:
- Username: `admin`
- Password: `admin123`

### Authorization Roles

- **ADMIN**: Full system access including deletions and reprocessing
- **HR**: CV processing and status updates
- **USER**: Read-only access to CVs and statistics

## Performance Tuning


### Database Configuration

For production, configure PostgreSQL:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/cvtool
    username: cvuser
    password: cvpass
    driver-class-name: org.postgresql.Driver
```

## Troubleshooting

### Common Issues

1. **Out of Memory**: Increase heap size or reduce batch size
2. **File Processing Errors**: Check file permissions and supported formats
3. **Database Connection**: Verify database configuration and connectivity
4. **Slow Processing**: Adjust thread pool sizes based on system capabilities

### Health Checks

Monitor application health via actuator endpoints:

```bash
curl http://localhost:8080/cv-tool/actuator/health
```






