plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
}

android {
  namespace = "com.drfxai.maximusvpn"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.drfxai.maximusvpn"
    minSdk = 24
    targetSdk = 36
    versionCode = (providers.gradleProperty("versionCode").orNull?.toIntOrNull() ?: 1)
    versionName = providers.gradleProperty("versionName").orElse("1.0.0").get()

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    ndk {
      // Xray-core (gomobile) native library architectures shipped in the APK
      abiFilters += listOf("arm64-v8a")
    }
  }

  signingConfigs {
    create("release") {
      // Only configure when a real keystore is provided (CI secrets or local env).
      // Empty/missing env must not crash configuration — release falls back to unsigned.
      val keystorePath = System.getenv("KEYSTORE_PATH").orEmpty().trim()
      val storePass = System.getenv("STORE_PASSWORD").orEmpty().trim()
      val keyPass = System.getenv("KEY_PASSWORD").orEmpty().trim()
      if (keystorePath.isNotEmpty() && storePass.isNotEmpty() && keyPass.isNotEmpty()) {
        storeFile = file(keystorePath)
        storePassword = storePass
        keyAlias = "upload"
        keyPassword = keyPass
      }
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      val releaseKeystore = System.getenv("KEYSTORE_PATH")
      val releaseStorePassword = System.getenv("STORE_PASSWORD")
      val releaseKeyPassword = System.getenv("KEY_PASSWORD")
      if (!releaseKeystore.isNullOrBlank() && !releaseStorePassword.isNullOrBlank() && !releaseKeyPassword.isNullOrBlank()) {
        signingConfig = signingConfigs.getByName("release")
      }
    }
    debug { signingConfig = signingConfigs.getByName("debugConfig") }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
  packaging {
    // libxray.so is a large gomobile library; keep default compression off for it
    jniLibs.useLegacyPackaging = false
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.okhttp)

  testImplementation(libs.androidx.core)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  "ksp"(libs.androidx.room.compiler)

  debugImplementation(libs.androidx.compose.ui.tooling)
}
