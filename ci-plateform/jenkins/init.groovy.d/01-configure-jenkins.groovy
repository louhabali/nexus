import jenkins.model.*
import hudson.security.*
import com.cloudbees.plugins.credentials.*
import com.cloudbees.plugins.credentials.domains.*
import com.cloudbees.plugins.credentials.impl.*
import org.jenkinsci.plugins.plaincredentials.impl.*
import hudson.util.Secret
import hudson.plugins.sonar.*
import hudson.plugins.sonar.model.TriggersConfig
import hudson.plugins.emailext.*
import hudson.tasks.Mailer

def instance = Jenkins.getInstance()

println "=========================================================="
println "--> STARTING AUTOMATED JENKINS INITIALIZATION"
println "=========================================================="

// reading environment variables for configuration 
def adminUser     = System.getenv('JENKINS_ADMIN_USER') ?: "admin"
def adminPass     = System.getenv('JENKINS_ADMIN_PASS') ?: "admin123"

def sonarTokenVal = System.getenv('SONAR_TOKEN') ?: ""
def smtpEmail     = System.getenv('SMTP_EMAIL') ?: ""
def smtpPassword  = System.getenv('SMTP_PASSWORD') ?: ""

// Microservices & Infrastructure Environment Variables
def jwtSecret           = System.getenv('JWT_SECRET') ?: ""
def sslKeystorePass     = System.getenv('SSL_KEYSTORE_PASSWORD') ?: ""
def kafkaClusterId      = System.getenv('KAFKA_CLUSTER_ID') ?: ""
def userDbUri           = System.getenv('USER_DB_URI') ?: ""
def productDbUri        = System.getenv('PRODUCT_DB_URI') ?: ""
def redisHost           = System.getenv('REDIS_HOST') ?: "redis"
def kafkaHost           = System.getenv('KAFKA_HOST') ?: "kafka:9092"
def mediaServiceUrl     = System.getenv('MEDIA_SERVICE_URL') ?: "http://media-service:8083/"
def userServiceUrl      = System.getenv('USER_SERVICE_URL') ?: "http://user-service:8081/"
def productServiceUrl   = System.getenv('PRODUCT_SERVICE_URL') ?: "http://product-service:8082/"

// configuring admin account
println "--> Configuring Admin User ('${adminUser}')..."
def hudsonRealm = new HudsonPrivateSecurityRealm(false)
hudsonRealm.createAccount(adminUser, adminPass)
instance.setSecurityRealm(hudsonRealm)

def strategy = new FullControlOnceLoggedInAuthorizationStrategy()
strategy.setAllowAnonymousRead(false)
instance.setAuthorizationStrategy(strategy)

// adding credentials
println "--> Setting up Global Credentials..."
def domain = Domain.global()
def store  = instance.getExtensionList('com.cloudbees.plugins.credentials.SystemCredentialsProvider')[0].getStore()

// A) SonarQube Token Credential
if (sonarTokenVal) {
    println "    -> Adding SonarQube Secret Text Credential ('sonar-token')"
    def sonarTokenCred = new StringCredentialsImpl(
        CredentialsScope.GLOBAL,
        "sonar-token",
        "Sonar Authentication Token",
        Secret.fromString(sonarTokenVal)
    )
    store.addCredentials(domain, sonarTokenCred)
}

// B) Production .env Secret File Credential ('01e-com-env-file')
println "    -> Dynamically constructing .env payload for microservices..."
def envFileContent = """# Automatically Generated Production .env File
APP_NAME=01e_com
SPRING_PROFILES_ACTIVE=prod

# Authentication & Security
JWT_SECRET=${jwtSecret}
SSL_KEYSTORE_PASSWORD=${sslKeystorePass}

# Event Streaming & Databases
KAFKA_CLUSTER_ID=${kafkaClusterId}
USER_DB_URI=${userDbUri}
PRODUCT_DB_URI=${productDbUri}

# Core Service Routers & Caching
REDIS_HOST=${redisHost}
KAFKA_HOST=${kafkaHost}
MEDIA_SERVICE_URL=${mediaServiceUrl}
USER_SERVICE_URL=${userServiceUrl}
PRODUCT_SERVICE_URL=${productServiceUrl}

# Quality & Notifications
SONAR_TOKEN=${sonarTokenVal}
SMTP_EMAIL=${smtpEmail}
"""

println "    -> Adding Secret File Credential ('01e-com-env-file')"
def envSecretFile = new FileCredentialsImpl(
    CredentialsScope.GLOBAL,
    "01e-com-env-file", // MUST match credentialsId in your Jenkinsfile!
    "Production Environment Secrets File for 01e_com Microservices",
    ".env",
    SecretBytes.fromBytes(envFileContent.getBytes("UTF-8"))
)
store.addCredentials(domain, envSecretFile)

// C) Docker Registry Credentials ('docker-registry-credentials')
println "    -> Adding Docker Registry Credentials ('docker-registry-credentials')"
def dockerRegistryUser = System.getenv('DOCKER_REGISTRY_USER') ?: "admin"
def dockerRegistryPass = System.getenv('DOCKER_REGISTRY_PASS') ?: "admin123"

def dockerRegistryCred = new UsernamePasswordCredentialsImpl(
    CredentialsScope.GLOBAL,
    "docker-registry-credentials",
    "Nexus Docker Registry Push Credentials",
    dockerRegistryUser,
    dockerRegistryPass
)
store.addCredentials(domain, dockerRegistryCred)

// configuring SonarQube Global Installation
println "--> Configuring SonarQube Global Installation..."
try {
    def sonarGlobalConfig = SonarGlobalConfiguration.get()
    
    // Enable "Environment variables" checkbox
    sonarGlobalConfig.setBuildWrapperEnabled(true)
    
    // Named-parameter instantiation prevents Java constructor signature mismatches
    def sonarInstallation = new SonarInstallation(
        "SonarQube",                  // name
        "http://sonarqube:9000",       // serverUrl
        SonarInstallation.DEFAULT_SQ_PLUGIN_VERSION, // sqPluginVersion
        "sonar-token",                 // serverAuthenticationToken
        "",                            // mojoVersion
        new TriggersConfig(),          // triggers
        ""                             // additionalAnalysisProperties
    )
    
    sonarGlobalConfig.setInstallations(sonarInstallation)
    sonarGlobalConfig.save()
    println "    -> SonarQube global settings applied successfully."
} catch (Exception e) {
    println "    [ERROR] Failed to configure SonarQube: " + e.getMessage()
}

// configuring Email Notifications
// configuring Email Notifications
if (smtpEmail && smtpPassword) {
    println "--> Configuring Email Notifications (${smtpEmail})..."
    
    // 1. Jenkins Location Configuration (Admin Address)
    def jlc = JenkinsLocationConfiguration.get()
    jlc.setAdminAddress(smtpEmail)
    jlc.save()

    // 2. Core Mailer Configuration
    def mailerDesc = instance.getDescriptorByType(Mailer.DescriptorImpl.class)
    mailerDesc.setSmtpHost("smtp.gmail.com")
    mailerDesc.setSmtpPort("465")
    mailerDesc.setUseSsl(true)
    mailerDesc.setSmtpAuth(smtpEmail, smtpPassword)
    mailerDesc.setReplyToAddress(smtpEmail)
    mailerDesc.save()

    // 3. Extended Email Plugin Configuration (email-ext)
    try {
        def extEmailDesc = instance.getDescriptorByType(ExtendedEmailPublisherDescriptor.class)
        if (extEmailDesc != null) {
            extEmailDesc.setSmtpServer("smtp.gmail.com")
            extEmailDesc.setSmtpPort("465")
            extEmailDesc.setUseSsl(true)
            extEmailDesc.setSmtpAuth(smtpEmail, smtpPassword)
            extEmailDesc.save()
            println "    -> Extended Email settings applied successfully."
        }
    } catch (Exception e) {
        println "    [ERROR] Failed to configure Extended Email: " + e.getMessage()
    }
} else {
    println "--> Skipping Email configuration: SMTP_EMAIL or SMTP_PASSWORD not set."
}
// saving all changes
instance.save()
println "=========================================================="
println "--> AUTOMATED JENKINS INITIALIZATION COMPLETE!"
println "=========================================================="