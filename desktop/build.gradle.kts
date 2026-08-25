import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.4.10"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.10"
    id("org.jetbrains.compose") version "1.11.1"
}

group = "com.drfxai"
def appVersion = (providers.gradleProperty("appVersion").orElse("1.0.0").get())
    .replaceFirst("^v", "")
// Debian requires: starts with digit, only alphanumerics . + - ~
if (!(appVersion =~ /^[0-9][A-Za-z0-9.+~-]*$/)) {
    logger.warn("Invalid appVersion '$appVersion' for Deb — falling back to 1.0.0")
    ext.set("validAppVersion", "1.0.0")
} else {
    ext.set("validAppVersion", appVersion)
}
version = ext.get("validAppVersion")


dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
}

compose.desktop {
    application {
        mainClass = "com.drfxai.maximus.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb)
            packageName = "Maximus VPN"
            packageVersion = version.toString()
            description = "Maximus VPN desktop client powered by Xray-core"
            vendor = "DrFXAi"
            copyright = "Copyright © 2026 DrFXAi"
        }
    }
}

kotlin {
    jvmToolchain(21)
}
