# Docker Registry Configuration Guide - Nexus

**Purpose:** Store and manage Docker container images in Nexus Repository Manager.

---

## Overview

Nexus provides three types of Docker repositories:

| Repository | Type | Purpose | Port |
|------------|------|---------|------|
| **docker-hosted** | Hosted | Store your built images | 8091 |
| **docker-group** | Group | Unified access to multiple repos | 8092 |
| **docker-proxy** | Proxy | Cache public Docker Hub images | 8093 |

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                                                           │
│                    Jenkins Pipeline                       │
│        (docker build + docker push)                       │
│                                                           │
└────────────────────────┬────────────────────────────────┘
                         │
                         ▼
         ┌───────────────────────────────┐
         │  Docker Hosted Registry       │
         │  (Port 8091 - Store images)   │
         │                               │
         │  ├─ 01e_com-gateway_service   │
         │  ├─ 01e_com-user_service      │
         │  ├─ 01e_com-product_service   │
         │  ├─ 01e_com-media_service     │
         │  ├─ 01e_com-cart_service      │
         │  ├─ 01e_com-order_service     │
         │  └─ 01e_com-frontend          │
         │                               │
         └───────────┬───────────────────┘
                     │
         ┌───────────┴───────────┐
         │                       │
         ▼                       ▼
    Docker Group          Docker Proxy
    (Port 8092)           (Port 8093)
    (Unified view)        (DockerHub cache)
```

---

## Configuration Steps

### 1. Create Docker Registries in Nexus

**Already Done in docker-compose.ci.yml:**
```yaml
ports:
  - "8091:8091" # Docker Hosted Registry
  - "8092:8092" # Docker Group Registry
  - "8093:8093" # Docker Proxy Registry
```

### 2. Verify Registries in Nexus UI

#### Access Nexus
```
http://localhost:8081
Username: admin
Password: admin123
```

#### Navigate to Repositories
```
Administration → Repositories
```

#### Expected Docker Repositories:
```
✓ docker-hosted
  - Type: Hosted
  - Format: Docker
  - HTTP Port: 8091
  - Status: Active

✓ docker-group
  - Type: Group
  - Format: Docker
  - HTTP Port: 8092
  - Members: docker-hosted, docker-proxy
  - Status: Active

✓ docker-proxy
  - Type: Proxy
  - Format: Docker
  - HTTP Port: 8093
  - Remote URL: https://registry-1.docker.io
  - Status: Active
```

### 3. Enable Allow Docker Anonymous Pull (Optional)

For production, restrict access. For development:

```
Administration → Repositories → docker-hosted
  → Configuration Tab
  → Scroll to "Allow anonymous docker pull"
  → Check if you want public pull access
  → Save
```

**Security Warning:** Only enable for development/staging. Disable in production.

---

## Jenkins Integration

### 1. Docker Login Script in Pipeline

The pipeline includes automatic Docker login:

```groovy
stage('Push Docker Images to Nexus Registry') {
    steps {
        withCredentials([
            usernamePassword(
                credentialsId: 'docker-registry-credentials',
                usernameVariable: 'DOCKER_REGISTRY_USER',
                passwordVariable: 'DOCKER_REGISTRY_PASS'
            )
        ]) {
            sh '''
                echo "${DOCKER_REGISTRY_PASS}" | docker login \
                    -u "${DOCKER_REGISTRY_USER}" \
                    --password-stdin nexus:8091
            '''
        }
    }
}
```

**What it does:**
- Authenticates to Nexus Docker registry using Jenkins credentials
- No plain-text passwords in logs (masked by Jenkins)
- Uses secure stdin for password input

### 2. Image Tagging

```groovy
# Tag image with Nexus registry hostname
docker tag 01e_com-gateway_service:latest \
    nexus:8091/01e_com-gateway_service:${BUILD_NUMBER}

# Tag with 'latest' for easy reference
docker tag 01e_com-gateway_service:latest \
    nexus:8091/01e_com-gateway_service:latest
```

**Why two tags?**
- `${BUILD_NUMBER}`: Unique identifier for this build (traceability)
- `latest`: Always points to most recent build (convenience)

### 3. Image Push

```groovy
# Push specific version
docker push nexus:8091/01e_com-gateway_service:${BUILD_NUMBER}

# Push latest
docker push nexus:8091/01e_com-gateway_service:latest
```

---

## Manual Docker Operations

### Login to Nexus Docker Registry

```bash
# From Jenkins container
docker exec jenkins docker login -u admin -p admin123 nexus:8091

# From your local machine (if connecting remotely)
docker login -u admin -p admin123 localhost:8091
```

**Response on success:**
```
Login Succeeded
```

### Build Local Image

```bash
# Build gateway service image
docker build -t 01e_com-gateway_service:1.0 gateway_service/

# Verify image exists
docker images | grep gateway_service
```

### Tag Image for Nexus

```bash
# Local to Nexus registry
docker tag 01e_com-gateway_service:1.0 \
    nexus:8091/01e_com-gateway_service:1.0
```

### Push to Nexus

```bash
docker push nexus:8091/01e_com-gateway_service:1.0
```

**Expected output:**
```
The push refers to repository [nexus:8091/01e_com-gateway_service]
5f70bf18a086: Pushed
...
1.0: digest: sha256:abc123... size: 5678
```

### Pull from Nexus

```bash
# Pull specific version
docker pull nexus:8091/01e_com-gateway_service:1.0

# Pull latest
docker pull nexus:8091/01e_com-gateway_service:latest
```

### List Images in Nexus

Via REST API:
```bash
curl -s -u admin:admin123 \
    http://localhost:8081/service/rest/v1/repositories/docker-hosted/components \
    | jq '.items[] | {name: .name, version: .version}'
```

**Response example:**
```json
{
  "name": "01e_com-gateway_service",
  "version": "123"
}
{
  "name": "01e_com-gateway_service",
  "version": "latest"
}
```

---

## Image Versioning Strategy

### Recommended Versioning

```
nexus:8091/SERVICE:BUILD_NUMBER      → Unique per build (e.g., :456)
nexus:8091/SERVICE:latest            → Latest build
nexus:8091/SERVICE:v1.0.0            → Semantic version (releases)
nexus:8091/SERVICE:main-COMMIT_SHA   → Branch-based (e.g., :main-abc123f)
```

### Our Implementation

The pipeline uses:
```groovy
IMAGE_TAG="${NEXUS_REGISTRY}/${SERVICE_IMAGE}:${BUILD_NUMBER}"
LATEST_TAG="${NEXUS_REGISTRY}/${SERVICE_IMAGE}:latest"
```

**Advantages:**
- `${BUILD_NUMBER}` = Jenkins build number (increments each build)
- Easy to trace which build an image came from
- `latest` tag always points to most recent

**Example:**
```
nexus:8091/01e_com-gateway_service:123
nexus:8091/01e_com-gateway_service:124
nexus:8091/01e_com-gateway_service:125
nexus:8091/01e_com-gateway_service:latest  → points to 125
```

---

## Using Docker Images in Production

### Option 1: Pull from Nexus in Docker Compose

Update `docker-compose.yml` to pull from Nexus:

```yaml
services:
  gateway:
    image: nexus:8091/01e_com-gateway_service:latest
    # or specific version:
    # image: nexus:8091/01e_com-gateway_service:125
    
  user-service:
    image: nexus:8091/01e_com-user_service:latest
```

### Option 2: Use Docker Group Registry

Use port 8092 to access all repositories via single endpoint:

```yaml
services:
  gateway:
    image: nexus:8092/01e_com-gateway_service:latest
```

Benefits:
- Unified namespace
- Automatic fallback to proxy registry
- Simplified docker-compose configuration

---

## Cleanup & Maintenance

### View Storage Usage

```bash
# In Nexus UI:
# Administration → Analyze → Repository Health Check

# Via API:
curl -s -u admin:admin123 \
    http://localhost:8081/service/rest/v1/repositories/docker-hosted/statistics \
    | jq '.byteSize'
```

### Delete Old Images

#### Via Nexus UI:
```
Browse → docker-hosted
  → Right-click image → Delete
```

#### Via Cleanup Task (Automated):
```
Administration → Tasks → Create Task
- Name: "Cleanup Old Docker Images"
- Type: "Delete components"
- Repository: "docker-hosted"
- Criteria: "Component age > 7 days"
- Frequency: Daily
```

#### Via API:
```bash
curl -X DELETE -u admin:admin123 \
    http://localhost:8081/service/rest/v1/repositories/docker-hosted/components/01e_com-gateway_service/1.0
```

### Backup Docker Images

```bash
# Export image as tar
docker save -o gateway_service_backup.tar \
    nexus:8091/01e_com-gateway_service:latest

# Load from backup
docker load -i gateway_service_backup.tar
```

---

## Troubleshooting

### Issue: "Docker login fails - unauthorized"

**Cause:** Incorrect credentials  
**Solution:**
```bash
# Verify credentials in Jenkins
docker exec jenkins env | grep DOCKER_REGISTRY

# Test credentials manually
docker login -u admin -p your_password localhost:8091
```

### Issue: "Failed to push image - repository does not exist"

**Cause:** Repository not created  
**Solution:**
```bash
# Create repository in Nexus UI:
# Administration → Repositories → Create Repository
# Name: docker-hosted
# Format: Docker
# HTTP Port: 8091
```

### Issue: "Docker image push succeeds but image not visible in Nexus"

**Cause:** Blob upload may be delayed  
**Solution:**
```bash
# Wait 30 seconds, then refresh
sleep 30

# Check via API
curl -s -u admin:admin123 \
    http://localhost:8081/service/rest/v1/repositories/docker-hosted/components

# Check logs
docker logs nexus | tail -20
```

### Issue: "Cannot pull image - connection refused"

**Cause:** Docker registry port not accessible  
**Solution:**
```bash
# Check Nexus container port mappings
docker ps | grep nexus

# Verify port 8091 is open
netstat -tlnp | grep 8091

# Test connectivity
curl -s http://localhost:8091 -v
```

---

## Performance Tips

1. **Use Docker Proxy Registry (8093):**
   - Caches public images locally
   - Reduces bandwidth, faster pulls

2. **Use Specific Image Versions:**
   - Bad: `nexus:8091/service:latest` (re-pulls every time)
   - Good: `nexus:8091/service:123` (cached)

3. **Multi-Stage Builds:**
   - Reduces image size
   - Faster pushes/pulls
   - Example: `gateway_service/dockerfile` (already implemented)

4. **Image Retention Policy:**
   - Keep last 10 builds
   - Delete images older than 30 days
   - Clean up via scheduled task

---

## Security Best Practices

1. **Separate Credentials per Environment:**
   ```
   Development:  docker-dev-user
   Staging:      docker-staging-user
   Production:   docker-prod-user (read-only recommended)
   ```

2. **Image Scanning:**
   - Enable image scanning in Nexus
   - Scan for vulnerabilities before deployment

3. **Don't Use Admin Credentials:**
   - Create service-specific user
   - Grant minimal permissions
   - See SECURITY.md

4. **Rotate Passwords Regularly:**
   - Update Jenkins credentials monthly
   - Update Nexus user passwords quarterly

5. **Use TLS/HTTPS in Production:**
   - Current setup uses plain HTTP (fine for local dev)
   - For production: Configure SSL/TLS certificates

---

## References

- **Nexus Docker Repository:** https://help.sonatype.com/repomanager3/nexus-repository-administration/formats/docker-registry
- **Docker Registry HTTP API:** https://docs.docker.com/registry/spec/api/
- **Docker Image Tagging:** https://docs.docker.com/engine/reference/commandline/tag/
- **Jenkins Credentials:** https://plugins.jenkins.io/credentials/

---

**Last Updated:** 2026-09-09  
**Document Version:** 1.0  
**Status:** Production Ready
