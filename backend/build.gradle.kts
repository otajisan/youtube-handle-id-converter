import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    kotlin("jvm") version "2.4.21"
    kotlin("plugin.spring") version "2.4.21"
    id("org.springframework.boot") version "4.1.2"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    jacoco
}

group = "io.github.otajisan"
version = "0.0.1-SNAPSHOT"
description = "YouTube handle <-> Channel ID converter backend"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))
    // springdoc 経由の swagger-core が jackson-bom 2.22.1 を要求し、Boot の BOM(2.21.x)より優先される。
    // 2.22.1 には high な脆弱性(GHSA-p6pp-m3f8-5c89 / GHSA-7hhh-6rmp-j9qf ほか)があるため修正版に固定する
    implementation(platform("com.fasterxml.jackson:jackson-bom:2.22.4"))
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-restclient")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-api:3.2.0")
    implementation("com.bucket4j:bucket4j_jdk17-core:8.21.0")
    implementation("com.github.ben-manes.caffeine:caffeine")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-restclient-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("io.mockk:mockk:1.14.11")
    testImplementation("com.ninja-squad:springmockk:5.0.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

ktlint {
    version = "1.8.0"
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Dockerfile / CD から version に依存せず参照できるよう出力名を固定する
tasks.bootJar {
    archiveFileName = "app.jar"
}

jacoco {
    toolVersion = "0.8.14"
}

// カバレッジ対象から除外するクラス(起動エントリポイントはテストで実行されない)
val jacocoExcludes = listOf("**/BackendApplicationKt.class")

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
    classDirectories.setFrom(
        classDirectories.files.map { fileTree(it) { exclude(jacocoExcludes) } },
    )
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    classDirectories.setFrom(
        classDirectories.files.map { fileTree(it) { exclude(jacocoExcludes) } },
    )
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

// `./gradlew check` で ktlint → test → jacoco 検証まで通す
tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
