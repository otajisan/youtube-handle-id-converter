import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    id("org.springframework.boot") version "4.1.1"
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
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("io.mockk:mockk:1.14.11")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    constraints {
        // Spring Boot 4.1.1 の BOM が管理する Tomcat 11.0.24 には critical な CVE がある
        // (CVE-2026-65182 / CVE-2026-65905 / CVE-2026-68525、11.0.25 で修正)。
        // BOM が 11.0.25 以上を管理するようになったらこの制約は削除する
        listOf("tomcat-embed-core", "tomcat-embed-el", "tomcat-embed-websocket").forEach {
            implementation("org.apache.tomcat.embed:$it:11.0.25") {
                because("CVE-2026-65182 / CVE-2026-65905 / CVE-2026-68525")
            }
        }
    }
}

ktlint {
    version = "1.8.0"
}

tasks.withType<Test> {
    useJUnitPlatform()
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
