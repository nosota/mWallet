# Development Setup

This guide will help you set up a local development environment for mWallet.

## Prerequisites

### Required Software

- **Java 23** (JDK)
- **Maven 3.8+**
- **PostgreSQL 15+** (or Docker for Testcontainers)
- **Docker** (for running tests with Testcontainers)
- **Git**

### Optional Tools

- **IntelliJ IDEA** or **Eclipse** (recommended IDEs)
- **pgAdmin** or **DBeaver** (database management)
- **Postman** or **cURL** (API testing)

## Installation

### 1. Clone the Repository

```bash
git clone <repository-url>
cd mWallet
```

### 2. Install Java 23

#### macOS (using Homebrew)
```bash
brew install openjdk@23
```

#### Linux (using SDKMAN)
```bash
sdk install java 23-open
sdk use java 23-open
```

#### Windows
Download and install from [Oracle](https://www.oracle.com/java/technologies/downloads/#java23) or [OpenJDK](https://openjdk.org/).

### 3. Verify Java Installation

```bash
java -version
# Should output: openjdk version "23" or similar
```

### 4. Install Maven

#### macOS
```bash
brew install maven
```

#### Linux
```bash
sudo apt-get install maven  # Debian/Ubuntu
sudo yum install maven      # RHEL/CentOS
```

#### Windows
Download from [Maven website](https://maven.apache.org/download.cgi) and add to PATH.

### 5. Verify Maven Installation

```bash
mvn -version
# Should show Maven 3.8+ and Java 23
```

## Database Setup

### Option 1: Local PostgreSQL Installation

#### Install PostgreSQL

##### macOS
```bash
brew install postgresql@16
brew services start postgresql@16
```

##### Linux
```bash
sudo apt-get install postgresql-16
sudo systemctl start postgresql
```

##### Windows
Download and install from [PostgreSQL website](https://www.postgresql.org/download/windows/).

#### Create Database

```bash
# Connect to PostgreSQL
psql postgres

# Create database and user
CREATE DATABASE mwallet;
CREATE USER mwallet_user WITH PASSWORD 'mwallet_pass';
GRANT ALL PRIVILEGES ON DATABASE mwallet TO mwallet_user;
\q
```

### Option 2: Docker PostgreSQL

```bash
docker run --name mwallet-postgres \
  -e POSTGRES_DB=mwallet \
  -e POSTGRES_USER=mwallet_user \
  -e POSTGRES_PASSWORD=mwallet_pass \
  -p 5432:5432 \
  -d postgres:16
```

### Option 3: Use Testcontainers (Recommended for Development)

No local PostgreSQL needed! Tests automatically spin up PostgreSQL in Docker.

**Requirements**:
- Docker Desktop running
- No configuration needed

## Project Structure

```
mWallet/
├── CLAUDE.md                     # Engineering standards
├── README.md                     # Project overview
├── RELEASES.md                   # Release notes
├── docs/                         # Documentation
│   ├── architecture/            # Architecture docs
│   ├── development/             # Development guides
│   ├── api/                     # API documentation
│   └── operations/              # Operations guides
└── service/                     # Main service module
    ├── pom.xml                  # Maven configuration
    └── src/
        ├── main/
        │   ├── java/
        │   │   └── com/nosota/mwallet/
        │   │       ├── MwalletApplication.java
        │   │       ├── dto/          # Data Transfer Objects
        │   │       ├── error/        # Custom exceptions
        │   │       ├── model/        # JPA entities
        │   │       ├── repository/   # Data access layer
        │   │       └── service/      # Business logic
        │   └── resources/
        │       ├── application.yaml  # Configuration
        │       └── db/migration/     # Flyway migrations
        └── test/
            └── java/
                └── com/nosota/mwallet/
                    ├── TestBase.java      # Test base class
                    └── tests/             # Test classes
```

## Configuration

### Application Configuration

Edit `service/src/main/resources/application.yaml`:

```yaml
spring:
  application:
    name: mwallet
  datasource:
    driver-class-name: org.postgresql.Driver
    url: ${JDBC_URL:jdbc:postgresql://localhost:5432/mwallet}
    username: ${JDBC_USER:mwallet_user}
    password: ${JDBC_PASSWORD:mwallet_pass}
    hikari:
      maximum-pool-size: 10
  jpa:
    hibernate:
      ddl-auto: validate  # Never use 'update' or 'create' in production
    show-sql: true        # Disable in production
  flyway:
    locations: classpath:db/migration
    enabled: true
```

### Environment Variables

For local development, you can set environment variables:

```bash
# macOS/Linux
export JDBC_URL=jdbc:postgresql://localhost:5432/mwallet
export JDBC_USER=mwallet_user
export JDBC_PASSWORD=mwallet_pass

# Windows PowerShell
$env:JDBC_URL="jdbc:postgresql://localhost:5432/mwallet"
$env:JDBC_USER="mwallet_user"
$env:JDBC_PASSWORD="mwallet_pass"
```

Or use an `.env` file (not committed to git):

```bash
# .env
JDBC_URL=jdbc:postgresql://localhost:5432/mwallet
JDBC_USER=mwallet_user
JDBC_PASSWORD=mwallet_pass
```

## Building the Project

### Compile and Package

```bash
cd service
mvn clean package
```

This will:
1. Compile Java sources
2. Process Lombok and MapStruct annotations
3. Run unit tests
4. Create JAR file in `target/`

### Skip Tests (for faster builds)

```bash
mvn clean package -DskipTests
```

### Generate MapStruct Mappers

MapStruct generates mapper implementations at compile time:

```bash
mvn clean compile
```

Check generated classes in `target/generated-sources/annotations/`.

## Running the Application

### Using Maven

```bash
cd service
mvn spring-boot:run
```

### Using JAR

```bash
cd service
mvn clean package
java -jar target/mwallet-0.0.1-SNAPSHOT.jar
```

### With Custom Configuration

```bash
java -jar target/mwallet-0.0.1-SNAPSHOT.jar \
  --spring.datasource.url=jdbc:postgresql://localhost:5432/mwallet \
  --spring.datasource.username=mwallet_user \
  --spring.datasource.password=mwallet_pass
```

### Verify Application Started

Check logs for:
```
Started MwalletApplication in X.XXX seconds
```

### Database Migrations

Flyway runs automatically on startup:
```
Flyway: Migrating schema "public" to version 1.01 - Initial schema
Flyway: Migrating schema "public" to version 1.02 - Reference id
...
```

## IDE Setup

### IntelliJ IDEA

#### 1. Import Project

- **File → Open** → Select `mWallet` directory
- IntelliJ auto-detects Maven project
- Wait for dependencies to download

#### 2. Enable Annotation Processing

- **Settings → Build, Execution, Deployment → Compiler → Annotation Processors**
- Check ✅ **Enable annotation processing**
- This is required for Lombok and MapStruct

#### 3. Install Lombok Plugin

- **Settings → Plugins**
- Search for "Lombok"
- Install and restart

#### 4. Configure SDK

- **File → Project Structure → Project**
- Set **SDK** to Java 23
- Set **Language Level** to 23

#### 5. Run Configuration

Create Application run configuration:
- **Main class**: `com.nosota.mwallet.MwalletApplication`
- **VM options**: (if needed)
  ```
  -Dspring.profiles.active=local
  ```
- **Environment variables**: (if needed)
  ```
  JDBC_URL=jdbc:postgresql://localhost:5432/mwallet
  JDBC_USER=mwallet_user
  JDBC_PASSWORD=mwallet_pass
  ```

### Eclipse

#### 1. Import Project

- **File → Import → Maven → Existing Maven Projects**
- Select `mWallet/service` directory
- Click **Finish**

#### 2. Install Lombok

- Download `lombok.jar` from [projectlombok.org](https://projectlombok.org/download)
- Run `java -jar lombok.jar`
- Select Eclipse installation directory
- Click **Install/Update**
- Restart Eclipse

#### 3. Enable Annotation Processing

- **Project → Properties → Java Compiler → Annotation Processing**
- Check ✅ **Enable project specific settings**
- Check ✅ **Enable annotation processing**

#### 4. Configure JRE

- **Project → Properties → Java Build Path → Libraries**
- Add **JRE System Library [JavaSE-23]**

### VS Code

#### 1. Install Extensions

- **Java Extension Pack**
- **Spring Boot Extension Pack**
- **Lombok Annotations Support**

#### 2. Configure Java

Edit `.vscode/settings.json`:
```json
{
  "java.configuration.runtimes": [
    {
      "name": "JavaSE-23",
      "path": "/path/to/jdk-23"
    }
  ],
  "java.jdt.ls.vmargs": "-javaagent:/path/to/lombok.jar"
}
```

## Development Workflow

### 1. Create Feature Branch

```bash
git checkout -b feature/my-feature
```

### 2. Make Changes

Follow coding standards in [CLAUDE.md](../../CLAUDE.md):
- Use Lombok annotations appropriately
- Follow package structure
- Write unit tests
- Keep methods under 40 LOC
- Keep classes under 300 LOC

### 3. Run Tests

```bash
mvn test
```

### 4. Check Code Style

Follow Google Java Style Guide (enforced in CLAUDE.md).

### 5. Commit Changes

```bash
git add .
git commit -m "feat: add new feature"
```

### 6. Push and Create PR

```bash
git push origin feature/my-feature
```

## Common Development Tasks

### Add New Entity

1. Create entity class in `model/` package
2. Create repository in `repository/` package
3. Create service in `service/` package
4. Create DTO in `dto/` package
5. Create mapper in `dto/` package
6. Write tests
7. Create Flyway migration if schema changes

### Add New Service Method

1. Define method in service interface (if applicable)
2. Implement in service class
3. Add validation annotations
4. Add `@Transactional` if needed
5. Write unit tests
6. Update API documentation

### Add Database Column

1. Create Flyway migration in `src/main/resources/db/migration/`
2. Name: `V1.XX__description.sql`
3. Add column:
   ```sql
   ALTER TABLE table_name
   ADD COLUMN new_column VARCHAR(255);
   ```
4. Update entity class
5. Update DTOs and mappers
6. Run application (Flyway auto-migrates)

### Debug Application

#### IntelliJ IDEA
- Set breakpoints in code
- Click **Debug** (Shift+F9)
- Use debugger controls

#### Remote Debugging
```bash
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 \
  -jar target/mwallet-0.0.1-SNAPSHOT.jar
```

Connect debugger to `localhost:5005`.

### View Database

#### Using psql
```bash
psql -h localhost -U mwallet_user -d mwallet

\dt              # List tables
\d wallet        # Describe wallet table
SELECT * FROM wallet LIMIT 10;
```

#### Using pgAdmin/DBeaver
- Connect to `localhost:5432`
- Database: `mwallet`
- User: `mwallet_user`
- Password: `mwallet_pass`

## Troubleshooting

### Lombok Not Working

**Symptom**: IDE shows errors like "cannot find symbol" for Lombok methods

**Solution**:
1. Enable annotation processing in IDE
2. Install Lombok plugin
3. Rebuild project: `mvn clean compile`
4. Restart IDE

### MapStruct Mappers Not Generated

**Symptom**: Mapper interface implementations not found

**Solution**:
```bash
mvn clean compile
```

Check `target/generated-sources/annotations/` for generated classes.

### Database Connection Refused

**Symptom**: `Connection refused` or `could not connect to server`

**Solution**:
1. Verify PostgreSQL is running:
   ```bash
   # macOS
   brew services list

   # Linux
   systemctl status postgresql

   # Docker
   docker ps | grep postgres
   ```
2. Check connection details in `application.yaml`
3. Verify port 5432 is not blocked

### Flyway Migration Failed

**Symptom**: `Migration checksum mismatch` or `Migration failed`

**Solution**:
1. Never modify existing migrations
2. Create new migration for fixes
3. If in development, drop and recreate database:
   ```sql
   DROP DATABASE mwallet;
   CREATE DATABASE mwallet;
   ```

### Tests Failing with Docker Error

**Symptom**: `Could not find a valid Docker environment`

**Solution**:
1. Start Docker Desktop
2. Verify Docker is running: `docker ps`
3. Testcontainers requires Docker to run

### Port Already in Use

**Symptom**: `Port 8080 already in use`

**Solution**:
1. Find process using port:
   ```bash
   # macOS/Linux
   lsof -i :8080

   # Windows
   netstat -ano | findstr :8080
   ```
2. Kill process or change application port:
   ```yaml
   server:
     port: 8081
   ```

## Next Steps

- Review [Architecture Overview](../architecture/overview.md)
- Read [Testing Guide](./testing.md)
- Explore [Service API Reference](../api/services.md)
- Check [CLAUDE.md](../../CLAUDE.md) for coding standards

## Related Documentation

- [Testing Guide](./testing.md) - Testing strategies and execution
- [Architecture Overview](../architecture/overview.md) - System architecture
- [CLAUDE.md](../../CLAUDE.md) - Engineering standards and policies
