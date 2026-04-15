plugins {
    id("java")
    id("org.springframework.boot") version "3.4.3"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.github.ben-manes.versions") version "0.51.0"
}

group = "net.osgiliath.module"
version = "0.1.0-SNAPSHOT"

java {
    sourceCompatibility = '21'
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation(platform(libs.spring.boot.starter.test))
    testImplementation(platform(libs.assertj.core))
    cucumberPlatform(libs.cucumber.junit.plugin)
    cucumberPlatform(libs.cucumber.guice.extension)

    // JaCoCo for code coverage reporting to CI/CD pipelines
    testImplementation("org.jacoco:org.jacoco.agent:0.8.9-runtime")
}

tasks.named("test") {
    useJUnitPlatform()
    
    jacoco {
        exclusions = ["net.osgiliath.module/**/*Test\$"]
    }
}

// JReleaser Maven publishing configuration
mavenPublishing {
    configurePublications {
        mavenJava {}
    }
}