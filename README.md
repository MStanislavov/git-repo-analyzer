# Git Repo Analyzer

A Spring Boot application that clones Git repositories, runs SonarQube static analysis, and collects code quality metrics — all orchestrated through an event-driven pipeline with a real-time web UI.

## What It Does

1. You submit one or more Git repository URLs (individually or via XML)
2. The app clones each repo locally
3. Runs the SonarQube Docker scanner against each repo
4. Collects metrics from SonarQube's API (bugs, vulnerabilities, tech debt, coverage, etc.)
5. Displays results in a dashboard with quality ratings

Each step happens asynchronously with concurrency controls, and progress is streamed to the browser in real time via Server-Sent Events.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 25, Spring Boot 4.0.4, Spring Modulith 2.0.4 |
| Frontend | React 19, Vite 8 |
| Database | PostgreSQL 16 |
| Analysis | SonarQube 10 Community (Docker scanner) |
| Docs | SpringDoc OpenAPI (Swagger UI) |

## Prerequisites

- **Java 25**
- **Maven 3.9.7+**
- **Git CLI**
- **Docker** (SonarQube scanner runs in a container)
- **Docker Compose** (for local SonarQube + PostgreSQL)

## Setup

### 1. Clone the repository

```bash
git clone https://github.com/CodeMaster10000/git-repo-analyzer.git
cd git-repo-analyzer
```

### 2. Start infrastructure

```bash
cp .env.example .env
# Edit .env with your passwords and paths
docker compose up -d
```

This starts:
- **SonarQube** on port `9000` (with its own PostgreSQL)
- **Application PostgreSQL** on port `5433`

### 3. Configure SonarQube

1. Open `http://localhost:9000` and log in (default: `admin`/`admin`)
2. Generate a user token under **My Account > Security > Tokens**
3. Add it to your `.env` file as `SONAR_TOKEN`

### 4. Build and run

```bash
mvn clean install
java -jar target/git-repo-analyzer-0.0.1-SNAPSHOT.jar
```

The app serves the frontend and API on **http://localhost:8080**.

API docs are available at **http://localhost:8080/swagger-ui.html**.

## Configuration

All configuration lives in `src/main/resources/application.properties` and can be overridden via environment variables in `.env`:

| Variable | Default | Description |
|----------|---------|-------------|
| `SONARQUBE_URL` | — | SonarQube server URL |
| `SONAR_TOKEN` | — | SonarQube authentication token |
| `CLONE_TARGET_DIR` | `D:/clone-dirs` | Where repos are cloned (also configurable in the UI) |
| `APP_DB_HOST` | `localhost` | PostgreSQL host |
| `APP_DB_PORT` | `5432` | PostgreSQL port |
| `APP_DB_USER` | `analyzer` | PostgreSQL username |
| `APP_DB_PASSWORD` | `analyzer` | PostgreSQL password |
| `APP_DB_NAME` | `git_repo_analyzer` | PostgreSQL database name |

Pipeline concurrency is tunable in `application.properties`:

```properties
pipeline.clone.max-concurrent=10
pipeline.sonar-analysis.max-concurrent=5
```

## Usage

### Analyze a single repository

In the **Analyze** tab, paste a Git URL and click **Start Analysis**. The pipeline progress appears in real time:

**Cloning → Analyzing → Collecting → Completed**

### Analyze multiple repositories

Upload an XML file or paste XML content in the bulk analysis section:

```xml
<repositories>
  <repository url="https://github.com/user/repo-one.git"/>
  <repository url="https://github.com/user/repo-two.git"/>
</repositories>
```

### View results

Switch to the **Dashboard** tab to see all analyzed repositories with:
- Quality gate status
- Tech debt (hours)
- Detailed metrics: lines of code, bugs, vulnerabilities, code smells, coverage, duplication, and A–E ratings for reliability, security, and maintainability

## Architecture

The application is an **event-driven modulith** built with Spring Modulith. Each stage of the pipeline is a separate module communicating through Spring application events:

```
POST /api/v1/analyze/url
  └─ CloningEventScheduler
       └─ GitRepoClonerService (virtual threads, semaphore-limited)
            └─ RepositoryClonedEvent
                 └─ RepositoryClonedListener (Docker sonar-scanner, semaphore-limited)
                      └─ RepositoryAnalyzedEvent
                           └─ SonarDataAnalyzedListener (SonarQube REST API)
                                └─ DataAnalyzedEvent
                                     └─ ApplicationEventListener (persist + SSE broadcast)
```

### Modules

| Module | Purpose |
|--------|---------|
| `scraper` | Clones repos via Git CLI using ProcessBuilder |
| `sonar_analyzer` | Runs SonarQube Docker scanner on cloned repos |
| `sonar_data_collector` | Queries SonarQube REST API for quality metrics |
| `persistence` | JPA entities and repositories (PostgreSQL) |
| `event` | Event definitions and enums |
| `app` | REST controller, SSE service, event listener |
| `config` | Concurrency configuration (semaphores, async) |

## REST API

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/analyze/url` | Analyze a single repo URL |
| `POST` | `/api/v1/analyze/xml` | Analyze repos from XML (file upload or text) |
| `GET` | `/api/v1/analyze/stream` | SSE stream for real-time pipeline progress |
| `GET` | `/api/v1/results` | Get all analysis results |
| `GET` | `/api/v1/results/{repoName}` | Get results for a specific repo |
| `DELETE` | `/api/v1/results/{id}` | Delete a repo and its cloned files |
| `DELETE` | `/api/v1/results/{id}/data` | Clear analysis data (keep the repo record) |
| `GET` | `/api/v1/config/clone-directory` | Get the clone directory |
| `PUT` | `/api/v1/config/clone-directory` | Set the clone directory |
| `POST` | `/api/v1/config/select-directory` | Open native folder picker |
