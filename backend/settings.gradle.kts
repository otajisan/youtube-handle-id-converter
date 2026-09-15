plugins {
    // JDK 25 がローカルに無い場合でも toolchain が自動でダウンロードする
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "backend"
