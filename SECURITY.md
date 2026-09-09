# Nexus Security & Access Control Configuration

**Purpose:** Implement user authentication, role-based access control (RBAC), and security best practices.

---

## Table of Contents
1. [Overview](#overview)
2. [Default Configuration](#default-configuration)
3. [Change Admin Password](#change-admin-password)
4. [Create Service Users](#create-service-users)
5. [Configure RBAC](#configure-rbac)
6. [Repository Permissions](#repository-permissions)
7. [API Token Authentication](#api-token-authentication)
8. [Security Audit Logging](#security-audit-logging)
9. [Disable Anonymous Access](#disable-anonymous-access)
10. [Production Hardening](#production-hardening)
11. [Troubleshooting](#troubleshooting)

---

## Overview

### Security Model

```
┌──────────────────────────────────────────────┐
│         Anonymous User                        │
│    (No credentials provided)                  │
└──────────────────┬───────────────────────────┘
                   │
                   ▼
        ┌──────────────────────┐
        │  Public Repositories?│
        │  (browse only)       │
        └──────────────────────┘
                   │
                   ▼
┌──────────────────────────────────────────────┐
│    Authenticated User                         │
│    (username/password OR API token)           │
└──────────────────┬───────────────────────────┘
                   │
        ┌──────────┴──────────┐
        ▼                     ▼
   ┌────────────┐      ┌────────────┐
   │ Role 1     │      │ Role 2     │
   │ (Viewer)   │      │ (Deployer) │
   └────────────┘      └────────────┘
        │                    │
        ▼                    ▼
  ┌──────────┐         ┌──────────┐
  │ Read     │         │ Read +   │
  │ Browse   │         │ Write    │
  │ Pull     │         │ Deploy   │
  └──────────┘         └──────────┘
```

---

## Default Configuration

### Current State (Development)
- **Admin User:** admin
- **Default Password:** admin123
- **Security Realm:** Local User Management
- **Anonymous Access:** Enabled
- **API Tokens:** Disabled

### Issues with Default
- ❌ Weak default password
- ❌ No role-based access
- ❌ Anyone can browse/download artifacts
- ❌ No audit trail
- ❌ Security realm not enterprise-grade

---

## Change Admin Password

### Via Nexus UI

**Step 1: Access Settings**
```
http://localhost:8081
Admin menu (top-right) → Account
```

**Step 2: Change Password**
```
Click "Change Password"
Old Password: admin123
New Password: YourNewSecurePassword123!
Confirm Password: YourNewSecurePassword123!
Save
```

### Via API

```bash
# Change admin password
curl -X PUT \
  -u admin:admin123 \
  -H "Content-Type: application/json" \
  -d '{"password":"NewPassword123!"}' \
  http://localhost:8081/service/rest/v1/users/admin/change-password
```

### Update Jenkins Credentials

After changing Nexus password:

**In Jenkins:**
```
Manage Jenkins → Manage Credentials
→ Global credentials
→ nexus-credentials
→ Edit
→ Update Password field
→ Save
```

---

## Create Service Users

### Purpose
Service users allow CI/CD systems (Jenkins, Docker CLI) to authenticate without using admin account.

### User Roles Strategy

| User | Purpose | Repositories | Permissions |
|------|---------|--------------|-------------|
| `ci-deployer` | Maven deployment | maven-releases, maven-snapshots | Deploy |
| `docker-pusher` | Docker push | docker-hosted | Push |
| `docker-puller` | Docker pull | docker-hosted, docker-proxy | Pull |
| `viewer` | Read-only access | All | Browse, Pull |

### Create CI Deployer User

**Step 1: Access Nexus UI**
```
Administration → Users
→ Create User
```

**Step 2: Create User**
```
User ID: ci-deployer
First Name: CI
Last Name: Deployer
Email: ci@example.com
Password: [Generate strong password]
Status: Active
Roles: [to be assigned]
```

Click **Create user**

**Step 3: Assign Role**
```
Administration → Roles
→ (We'll create custom role next)
```

### Create Docker Pusher User

```
User ID: docker-pusher
First Name: Docker
Last Name: Pusher
Email: docker@example.com
Password: [Generate strong password]
Status: Active
```

### Generate Strong Passwords

Use: `openssl rand -base64 20` or online generator

Example strong password: `K8x$mP2@nL9#vQ5!wR3%`

---

## Configure RBAC

### Create Custom Roles

#### Role 1: Maven Deployer

**Step 1: Access Nexus**
```
Administration → Roles
→ Create Role
```

**Step 2: Define Role**
```
Role ID: nx-deployer-maven
Role Name: Maven Deployer
Role Description: Deploy Maven artifacts to releases/snapshots
```

**Step 3: Assign Privileges**
Select these privileges:
```
✓ nx-repository-view-maven-maven-releases-browse
✓ nx-repository-view-maven-maven-releases-read
✓ nx-repository-view-maven-maven-releases-edit
✓ nx-repository-view-maven-maven-releases-delete
✓ nx-repository-view-maven-maven-snapshots-browse
✓ nx-repository-view-maven-maven-snapshots-read
✓ nx-repository-view-maven-maven-snapshots-edit
✓ nx-repository-view-maven-maven-snapshots-delete
✓ nx-repository-view-maven-maven-public-read
✓ nx-repository-view-maven-maven-public-browse
```

Click **Create role**

#### Role 2: Docker Pusher

```
Administration → Roles → Create Role

Role ID: nx-pusher-docker
Role Name: Docker Registry Pusher
Role Description: Push Docker images to hosted registry
```

**Assign Privileges:**
```
✓ nx-repository-view-docker-docker-hosted-browse
✓ nx-repository-view-docker-docker-hosted-read
✓ nx-repository-view-docker-docker-hosted-edit
✓ nx-repository-view-docker-docker-hosted-delete
✓ nx-repository-view-docker-docker-hosted-add
```

#### Role 3: Viewer (Read-Only)

```
Administration → Roles → Create Role

Role ID: nx-viewer
Role Name: Repository Viewer
Role Description: Read and browse all repositories
```

**Assign Privileges:**
```
✓ nx-repository-view-*-*-browse
✓ nx-repository-view-*-*-read
✓ ui-repo-browser-tree-view
```

### Assign Roles to Users

**For ci-deployer:**
```
Administration → Users
→ ci-deployer
→ Edit
→ Roles: Add "Maven Deployer"
→ Save
```

**For docker-pusher:**
```
Administration → Users
→ docker-pusher
→ Edit
→ Roles: Add "Docker Registry Pusher"
→ Save
```

---

## Repository Permissions

### Maven Releases (Production)

```
Administration → Repositories
→ maven-releases
→ Configuration Tab
→ Deployment Policy: "Disable redeploy"
→ Save

# Rationale: Prevent accidental overwrite of released versions
```

### Maven Snapshots (Development)

```
Administration → Repositories
→ maven-snapshots
→ Configuration Tab
→ Deployment Policy: "Allow redeploy"
→ Component Age: "Keep last 30 days"
→ Save

# Rationale: Allow frequent deployments, cleanup old versions
```

### Docker Registry (Strict)

```
Administration → Repositories
→ docker-hosted
→ Configuration Tab
→ Allow anonymous docker pull: Unchecked
→ Save

# Rationale: Require authentication for all operations
```

---

## API Token Authentication

### Why Use API Tokens?

```
✓ No password in logs or configs
✓ Revokable without changing password
✓ Scoped to specific permissions
✓ Audit trail per token
```

### Generate API Token

**Step 1: Access Account Settings**
```
http://localhost:8081
→ Top-right menu → Account
```

**Step 2: Create Token**
```
Click "New Access Token"
Token Name: "Jenkins CI Deployment"
Save
```

**Step 3: Copy Token**
```
Token appears: xxxxxxxxxxxxxxxxxxxx_xxxxx
Keep this secure!
```

### Use API Token in Jenkins

**Instead of username/password:**
```groovy
withCredentials([
    usernamePassword(
        credentialsId: 'nexus-credentials',
        usernameVariable: 'NEXUS_USER',
        passwordVariable: 'NEXUS_TOKEN'
    )
]) {
    sh '''
        # Use token as password
        ./mvnw deploy \
            -Dnexus.username=${NEXUS_USER} \
            -Dnexus.password=${NEXUS_TOKEN}
    '''
}
```

### Revoke API Token

```
Administration → Users
→ (your user) → Edit
→ Manage Tokens
→ Delete token if compromised
```

---

## Security Audit Logging

### Enable Audit Logging

**Step 1: Access System Log**
```
Administration → System Logs
```

**Step 2: Configure Logging Level**
```
Set to: DEBUG or TRACE (for security events)
```

**Step 3: View Logs**

Audit events logged:
```
- User login/logout
- Artifact deploy
- Artifact download
- User/role changes
- Configuration changes
```

### View Audit Logs

```bash
# Via Docker
docker logs nexus | grep -i "audit\|security\|user"

# Via Nexus UI
Administration → System Logs
→ Search for events
→ Filter by timestamp, user, action
```

### Export Audit Logs

```bash
# Get logs via API
curl -s -u admin:password \
  http://localhost:8081/service/rest/v1/status \
  | jq '.status'

# Collect container logs
docker logs nexus > nexus-audit.log
```

---

## Disable Anonymous Access

### Remove Anonymous Role

**Step 1: Access Security Settings**
```
Administration → System → Security
→ Anonymous Access: Unchecked
```

**Step 2: Enable SMTP (Email Verification)**
```
Administration → System → Email Server
- SMTP Host: smtp.gmail.com
- SMTP Port: 587
- Username: your-email@gmail.com
- Password: app-specific-password
- From Address: noreply@company.com
- Test Email Sending
```

**Step 3: Enable User Creation (Optional)**
```
Administration → System → Security
→ Allow users to create accounts: Checked or Unchecked (your choice)
```

---

## Production Hardening

### Checklist

- [ ] Change admin password
- [ ] Disable anonymous access
- [ ] Create service users per role
- [ ] Configure RBAC roles
- [ ] Set repository deployment policies
- [ ] Enable audit logging
- [ ] Configure API tokens
- [ ] Set up LDAP/AD integration (optional)
- [ ] Enable HTTPS/TLS
- [ ] Configure backup procedures
- [ ] Set up monitoring/alerting

### LDAP/AD Integration (Enterprise)

**For large organizations:**

```
Administration → System → Authentication
→ LDAP Tab
→ Configure LDAP Server
- LDAP Server: ldap.company.com
- Base DN: dc=company,dc=com
- Configuration: Test connection
→ Save
```

### Enable HTTPS/TLS

```bash
# Generate self-signed certificate (development)
openssl req -x509 -nodes -days 365 \
  -newkey rsa:2048 \
  -keyout nexus.key \
  -out nexus.crt

# For production: Use CA-signed certificate

# Update docker-compose to mount cert
docker-compose.ci.yml:
  nexus:
    volumes:
      - nexus-data:/nexus-data
      - ./nexus.crt:/etc/ssl/nexus.crt
      - ./nexus.key:/etc/ssl/nexus.key
```

---

## Troubleshooting

### Issue: "Cannot login - user not found"

**Cause:** User not created or typo  
**Solution:**
```bash
# Verify user exists
curl -s -u admin:admin123 \
  http://localhost:8081/service/rest/v1/users | jq '.[] | .id'

# Create user if missing
# Use Nexus UI: Administration → Users → Create User
```

### Issue: "403 Forbidden - cannot deploy"

**Cause:** User lacks deploy permissions  
**Solution:**
```bash
# Check user roles
curl -s -u admin:admin123 \
  http://localhost:8081/service/rest/v1/users/ci-deployer | jq .roles

# Verify role has deploy privilege
# Administration → Roles → [role-name]
# Add missing privileges
```

### Issue: "401 Unauthorized - invalid API token"

**Cause:** Token expired or revoked  
**Solution:**
```bash
# Regenerate token
Administration → Users → [user]
→ Manage Tokens
→ Create new token
→ Update Jenkins credentials
```

### Issue: "Docker login fails after user creation"

**Cause:** Docker realm not configured  
**Solution:**
```bash
# Ensure nexus is on network accessible to Jenkins
docker network inspect ci_net

# Verify credentials are correct
docker exec jenkins docker login -u docker-pusher -p password nexus:8091

# Check Nexus is accepting Docker connections
docker logs nexus | tail -20
```

---

## Compliance & Standards

### SOC 2 Requirements
- ✅ User authentication (LDAP/AD)
- ✅ Role-based access control
- ✅ Audit logging
- ✅ Password policies
- ✅ Token management
- ⚠️ TLS/HTTPS (setup separately)
- ⚠️ MFA (available with Enterprise)

### PCI DSS Requirements
- ✅ Strong passwords (enforce via settings)
- ✅ Access controls (RBAC)
- ✅ Audit logs
- ⚠️ Encryption at rest (configure storage)
- ⚠️ Encryption in transit (TLS/HTTPS)
- ⚠️ Regular backups

---

## Quick Reference

### Common Commands

```bash
# Create user via API
curl -X POST -u admin:admin123 \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "ci-deployer",
    "firstName": "CI",
    "lastName": "System",
    "emailAddress": "ci@example.com",
    "password": "SecurePass123!",
    "roles": ["nx-deployer-maven"]
  }' \
  http://localhost:8081/service/rest/v1/users

# List all users
curl -s -u admin:admin123 \
  http://localhost:8081/service/rest/v1/users | jq '.[] | {id, firstName, lastName}'

# Delete user
curl -X DELETE -u admin:admin123 \
  http://localhost:8081/service/rest/v1/users/ci-deployer

# List roles
curl -s -u admin:admin123 \
  http://localhost:8081/service/rest/v1/roles | jq '.[] | .id'
```

---

## References

- **Nexus Security:** https://help.sonatype.com/repomanager3/nexus-repository-administration/security
- **RBAC Guide:** https://help.sonatype.com/repomanager3/nexus-repository-administration/security/users-and-roles
- **API Authentication:** https://help.sonatype.com/repomanager3/nexus-repository-administration/accessing-nexus/nexus-rest-api-authentication
- **LDAP Integration:** https://help.sonatype.com/repomanager3/nexus-repository-administration/security/realms

---

**Last Updated:** 2026-09-09  
**Document Version:** 1.0  
**Status:** Production Ready
