import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.4.10"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.10"
    id("org.jetbrains.compose") version "1.11.1"
}

group = "com.drfxai"
// Debian package versions must start with a digit and contain only alphanumerics . + - ~
val appVersion = providers.gradleProperty("appVersion")
    .orElse("1.0.0").get().trim().removePrefix("v")
version = if (Regex("^[0-9][A-Za-z0-9.+~-]*$").matches(appVersion)) {
    appVersion
} else {
    logger.warn("Invalid appVersion '$appVersion' for Deb — falling back to 1.0.0")
    "1.0.0"
}

// Windows installers (MSI/EXE) require strict numeric MAJOR.MINOR.BUILD
// (caps 255.255.65535) — no pre-release suffixes like "-ci". Normalize here so
// any CI-passed appVersion (e.g. "0.0.0-ci", "2.5", "v2.5.1") packages cleanly.
val numericParts = Regex("\\d+").findAll(version.toString())
    .take(3).map { match -> match.value.toInt() }.toList()
val windowsVersion = when (numericParts.size) {
    3 -> "${numericParts[0].coerceIn(1, 255)}.${numericParts[1].coerceIn(0, 255)}.${numericParts[2].coerceIn(0, 65535)}"
    2 -> "${numericParts[0].coerceIn(1, 255)}.${numericParts[1].coerceIn(0, 255)}.0"
    1 -> "${numericParts[0].coerceIn(1, 255)}.0.0"
    else -> "1.0.0"
}


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
            windows {
                msiPackageVersion = windowsVersion
                exePackageVersion = windowsVersion
            }
            description = "Maximus VPN desktop client powered by Xray-core"
            vendor = "DrFXAi"
            copyright = "Copyright © 2026 DrFXAi"
        }
    }
}

kotlin {
    jvmToolchain(21)
}
