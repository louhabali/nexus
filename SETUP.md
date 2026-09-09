# 01E-COM Microservices Platform - Complete Setup Guide

## Table of Contents
1. [Prerequisites](#prerequisites)
2. [Initial Setup](#initial-setup)
3. [Docker Configuration](#docker-configuration)
4. [Jenkins Setup](#jenkins-setup)
5. [Nexus Repository Setup](#nexus-repository-setup)
6. [Pipeline Configuration](#pipeline-configuration)
7. [Testing & Validation](#testing--validation)
8. [Troubleshooting](#troubleshooting)

---

## Prerequisites

### System Requirements
- **OS:** Linux or macOS (tested on Ubuntu 22.04, macOS 12+)
- **Docker:** 20.10+ with rootless mode support
- **Docker Compose:** 2.0+
- **Git:** 2.30+
- **Java:** 21+ (for local Maven builds)
- **Maven:** 3.9+ (included via mvnw)
- **Node.js:** 18+ (for Angular frontend)
- **Disk Space:** Minimum 50GB for Docker images and volumes
- **RAM:** Minimum 8GB (16GB recommended)

### Installed Software Versions Used
```bash
# Check your versions
docker --version        # Docker version 24.0+
docker compose version  # Docker Compose version 2.20+
java -version           # Java 21
mvn -version           # Maven 3.9+
node --version         # Node.js 18+
```

---

## Initial Setup

### 1. Clone Repository
```bash
cd ~
git clone <your-repo-url>
cd nexus
```

### 2. Create Environment File
Create `.env` file in the root directory:
```bash
cp .env.example .env
```

Edit `.env` with your configuration:
```env
# === APPLICATION CONFIGURATION ===
APP_NAME=01e_com
SPRING_PROFILES_ACTIVE=dev

# === AUTHENTICATION & SECURITY ===
JWT_SECRET=your-super-secret-jwt-key-min-32-characters-long-here
SSL_KEYSTORE_PASSWORD=your-ssl-keystore-password

# === DATABASE CONFIGURATION ===
USER_DB_URI=mongodb://mongodb:27017/user_db
PRODUCT_DB_URI=mongodb://mongodb:27017/product_db
CART_DB_URI=mongodb://mongodb:27017/cart_db
ORDER_DB_URI=mongodb://mongodb:27017/order_db

# === CACHE & MESSAGE QUEUE ===
REDIS_HOST=redis
REDIS_PORT=6379
KAFKA_CLUSTER_ID=MkQkQjQ1NTk0NTI1NDExNDQ2NTgyODU=
KAFKA_HOST=kafka:9092

# === SERVICE URLS ===
USER_SERVICE_URL=http://user-service:8081/
PRODUCT_SERVICE_URL=http://product-service:8082/
MEDIA_SERVICE_URL=http://media-service:8083/
CART_SERVICE_URL=http://cart-service:8085/
ORDER_SERVICE_URL=http://order-service:8084/

# === CI/CD CONFIGURATION ===
SONAR_TOKEN=your-sonarqube-token-here
DOCKER_REGISTRY_USER=admin
DOCKER_REGISTRY_PASS=admin123

# === EMAIL NOTIFICATIONS ===
SMTP_EMAIL=your-email@gmail.com
SMTP_PASSWORD=your-app-specific-password

# === JENKINS ADMIN ===
JENKINS_ADMIN_USER=admin
JENKINS_ADMIN_PASS=admin123

# === NEXUS CONFIGURATION ===
NEXUS_USERNAME=admin
NEXUS_PASSWORD=admin123
```

### 3. Create Nexus Settings File
Create `maven-settings.xml` in the root (template already exists):

```bash
# Copy the template
cp maven-settings.xml ci-plateform/maven-settings.xml

# This file will be uploaded to Jenkins as credential: nexus-settings
```

---

## Docker Configuration

### 1. Enable Docker Rootless Mode (Recommended)
```bash
# Install Docker in rootless mode
./install-docker-rootless.zsh

# Verify rootless socket
ls -la /run/user/$(id -u)/docker.sock

# Test
docker ps
```

### 2. Start CI/CD Platform Stack
```bash
cd ci-plateform

# Build and start all services (Jenkins, SonarQube, PostgreSQL, Nexus)
docker compose -f docker-compose.ci.yml up -d --build

# Wait for services to be ready (30-60 seconds)
sleep 60

# Verify all services are running
docker compose -f docker-compose.ci.yml ps
```

**Expected Output:**
```
NAME         STATUS        PORTS
jenkins      running       8090->8080, 50000->50000
sonarqube    running       9000->9000
sonar_db     running       (internal)
nexus        running       8081->8081, 8091-8093->8091-8093
```

### 3. Verify Services
```bash
# Check Jenkins
curl -s http://localhost:8090 | grep -i jenkins | head -5

# Check SonarQube
curl -s http://localhost:9000/api/system/health | jq .

# Check Nexus
curl -s http://localhost:8081/service/rest/v1/status | jq .
```

---

## Jenkins Setup

### 1. Access Jenkins UI
- **URL:** http://localhost:8090
- **Username:** admin
- **Password:** admin123 (from .env)

### 2. Verify Automatic Configuration
Jenkins automatically initializes via `ci-plateform/jenkins/init.groovy.d/01-configure-jenkins.groovy`:

✅ **Configured automatically:**
- Admin user account
- Global credentials (SonarQube token, .env file, Docker registry credentials)
- SonarQube global installation
- Email notification setup
- GitHub webhook configuration

### 3. Check Credentials
Navigate to: **Manage Jenkins → Manage Credentials → System → Global credentials**

Should see these credentials:
```
1. sonar-token          (Secret text)
2. 01e-com-env-file     (Secret file)
3. docker-registry-credentials (Username/Password)
4. nexus-credentials    (Username/Password)
5. nexus-settings       (Secret file)
```

### 4. Verify Docker Access
```bash
# Test Jenkins Docker connectivity
docker exec jenkins docker ps

# Expected: Docker daemon is accessible, shows running containers
```

If this fails, see [Troubleshooting](#troubleshooting) section.

---

## Nexus Repository Setup

### 1. Access Nexus UI
- **URL:** http://localhost:8081
- **Username:** admin
- **Password:** admin123 (default)

### 2. Initial Setup Wizard
First login will prompt for initial setup:
- [ ] Click "Configure Nexus"
- [ ] Accept default settings
- [ ] Create admin password (recommended: change from default)

### 3. Verify Maven Repositories Exist
Navigate to: **Administration → Repositories**

Expected repositories:
```
✓ maven-releases      (Type: Hosted, Format: Maven)
✓ maven-snapshots     (Type: Hosted, Format: Maven)
✓ maven-public        (Type: Group, Format: Maven)
✓ maven-central       (Type: Proxy, Format: Maven)
```

### 4. Verify Docker Registries Exist
Navigate to: **Administration → Repositories**

Look for Docker repositories:
```
✓ docker-hosted       (Type: Hosted, Format: Docker, Port: 8091)
✓ docker-group        (Type: Group, Format: Docker, Port: 8092)
✓ docker-proxy        (Type: Proxy, Format: Docker, Port: 8093)
```

If Docker repositories don't exist, see [DOCKER_REGISTRY.md](DOCKER_REGISTRY.md).

### 5. Create Service User for CI/CD (Recommended)
See [SECURITY.md](SECURITY.md) for detailed user and role setup.

---

## Pipeline Configuration

### 1. Create GitHub Webhook
In Jenkins: **Manage Jenkins → Configure System → GitHub**
- Set GitHub server URL
- Add authentication token
- Save configuration

### 2. Create Jenkins Pipeline Job
```bash
# Create new Pipeline job in Jenkins UI
Job Name: 01e-com-pipeline
Pipeline Script: Pipeline script from SCM
SCM: Git
Repository URL: https://github.com/your-org/01e-com.git
Branch: */pipeline
Script Path: Jenkinsfile
```

### 3. Verify Branch Protection
On GitHub, ensure `pipeline` branch is protected:
```
Settings → Branches → Branch Protection Rules
- Require pull request reviews
- Require status checks to pass
- Require branches to be up to date before merging
```

### 4. Create Required Jenkins Credentials Manually

If not automatically created, add these in Jenkins:

#### A. Nexus Maven Credentials
```
Type: Username and password
ID: nexus-credentials
Username: admin (or your Nexus user)
Password: admin123 (or your Nexus password)
```

#### B. Docker Registry Credentials
```
Type: Username and password
ID: docker-registry-credentials
Username: admin (or your Nexus user)
Password: admin123 (or your Nexus password)
```

#### C. Nexus Settings File
```
Type: Secret file
ID: nexus-settings
File: (upload ci-plateform/maven-settings.xml)
```

#### D. SonarQube Token
```
Type: Secret text
ID: sonar-token
Secret: (get from SonarQube: User → Security → Generate token)
```

---

## Testing & Validation

### Phase 1: Verify Infrastructure
```bash
# 1. Check all containers running
docker compose -f ci-plateform/docker-compose.ci.yml ps

# 2. Test Jenkins API
curl -s -u admin:admin123 http://localhost:8090/api/json | jq .jobs[].name

# 3. Test SonarQube API
curl -s http://localhost:9000/api/system/status | jq .

# 4. Test Nexus API
curl -s -u admin:admin123 http://localhost:8081/service/rest/v1/repositories | jq '.[] | {name, type, format}'
```

### Phase 2: Test Maven Deployment
```bash
# 1. Navigate to a service
cd gateway_service

# 2. Deploy to Nexus
./mvnw deploy -DskipTests \
  -Dnexus.username=admin \
  -Dnexus.password=admin123

# 3. Verify in Nexus UI
# Navigate to: http://localhost:8081
# Browse → maven-snapshots → buy01 → gateway_service
```

### Phase 3: Test Docker Image Build and Push
```bash
# 1. Build all services locally
docker compose build

# 2. Login to Nexus Docker registry
docker login -u admin -p admin123 localhost:8091

# 3. Tag an image
docker tag 01e_com-gateway_service:latest localhost:8091/01e_com-gateway_service:v1.0

# 4. Push to Nexus
docker push localhost:8091/01e_com-gateway_service:v1.0

# 5. Verify in Nexus UI
# Navigate to: http://localhost:8081
# Browse → docker-hosted → 01e_com-gateway_service
```

### Phase 4: Run Full Pipeline
```bash
# 1. Commit code to pipeline branch
git add .
git commit -m "Update: Add Nexus deployment and Docker registry push"
git push origin pipeline

# 2. Monitor Jenkins build
# Go to http://localhost:8090 → 01e-com-pipeline
# Watch the build progress through all stages

# 3. Expected stages:
# ✓ Checkout & Setup Env
# ✓ Parallel Automated Testing
# ✓ SonarQube Scanner Analysis
# ✓ Quality Gate Check
# ✓ Build Artifacts & Container Images
# ✓ Deploy Maven Artifacts to Nexus        [NEW]
# ✓ Push Docker Images to Nexus Registry   [NEW]
# ✓ Start app stack for E2E
# ✓ E2E readiness smoke check
# ✓ Browser E2E tests
# ✓ Stop application stack
# ✓ Deploy Application
```

### Phase 5: Verify Artifacts in Nexus
```bash
# Maven Artifacts
# Navigate to: http://localhost:8081/repository/maven-snapshots/buy01/

# Docker Images
# Navigate to: http://localhost:8081/repository/docker-hosted/

# Expected images:
# - 01e_com-gateway_service:latest
# - 01e_com-user_service:latest
# - 01e_com-product_service:latest
# - 01e_com-media_service:latest
# - 01e_com-cart_service:latest
# - 01e_com-order_service:latest
# - 01e_com-frontend:latest
```

---

## Troubleshooting

### Issue: "Cannot connect to the Docker daemon"
**Cause:** Jenkins can't access Docker socket  
**Solution:**
```bash
# Check if rootless Docker socket exists
ls -la /run/user/1083/docker.sock

# Verify Jenkins volume mount in docker-compose.ci.yml
grep -A 2 "volumes:" ci-plateform/docker-compose.ci.yml | grep docker.sock

# Restart Jenkins
docker compose -f ci-plateform/docker-compose.ci.yml restart jenkins

# Test Docker access
docker exec jenkins docker ps
```

### Issue: "Failed to authenticate with Nexus"
**Cause:** Credentials mismatch  
**Solution:**
```bash
# Verify .env file has correct credentials
grep NEXUS ci-plateform/.env

# Check Jenkins credentials
# Manage Jenkins → Manage Credentials → System → Global credentials
# Verify nexus-credentials ID matches Jenkinsfile reference

# Test manually
curl -s -u admin:admin123 http://localhost:8081/service/rest/v1/status | jq .
```

### Issue: "Docker image push fails - unauthorized"
**Cause:** Docker registry credentials not set  
**Solution:**
```bash
# Verify Jenkins credential exists
docker exec jenkins ls -la /var/jenkins_home/credentials.xml

# Re-add credentials in Jenkins UI:
# Manage Jenkins → Manage Credentials → System → Global credentials
# Add → Username and password
# ID: docker-registry-credentials
# Username/Password from .env DOCKER_REGISTRY_USER/PASS

# Restart Jenkins
docker compose -f ci-plateform/docker-compose.ci.yml restart jenkins
```

### Issue: "Nexus runs out of disk space"
**Cause:** Default volume size insufficient  
**Solution:**
```bash
# Check volume usage
docker exec nexus df -h /nexus-data

# Increase Docker volume driver size (if using loop device)
# Or redirect volume to larger disk mount point

# Implement cleanup task in Nexus:
# Administration → Tasks → Create Task
# Type: Delete components
# Repository: maven-snapshots
# Criteria: Component age > 30 days
```

### Issue: "SonarQube not reaching from Jenkins"
**Cause:** Network or DNS resolution  
**Solution:**
```bash
# Test from Jenkins container
docker exec jenkins curl -s http://sonarqube:9000/api/system/status

# Verify both containers on same network
docker network inspect ci-plateform_ci_net | grep -E '"Name":|"IPv4Address"'

# Verify SonarQube health
curl -s http://localhost:9000/api/system/health | jq .
```

### Issue: "Maven deployment fails - unable to find pom.xml"
**Cause:** Wrong working directory  
**Solution:**
```bash
# Verify pom.xml locations
find . -name pom.xml -type f

# Check Jenkinsfile deploy stage uses cd $service
# Each service directory should have pom.xml

# Test manually
cd gateway_service && ls -la pom.xml
```

---

## Next Steps

1. **Implement Security** → See [SECURITY.md](SECURITY.md)
2. **Configure Docker Registry** → See [DOCKER_REGISTRY.md](DOCKER_REGISTRY.md)
3. **Monitor Pipeline** → Set up Jenkins job notifications
4. **Scale Services** → Add Kubernetes deployment (optional)
5. **Backup Strategy** → Implement Nexus and Jenkins backup procedures

---

## Support & Resources

- **Nexus Documentation:** https://help.sonatype.com/repomanager3
- **Jenkins Documentation:** https://www.jenkins.io/doc/
- **SonarQube Documentation:** https://docs.sonarqube.org/latest/
- **Docker Documentation:** https://docs.docker.com/
- **Spring Boot Documentation:** https://spring.io/projects/spring-boot

---

**Last Updated:** 2026-09-09  
**Document Version:** 1.0  
**Status:** Production Ready
