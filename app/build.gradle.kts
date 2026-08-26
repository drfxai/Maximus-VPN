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
    versionCode = (providers.gradleProperty("versionCode").orNull?.toIntOrNull() ?: 253)
    versionName = providers.gradleProperty("versionName").orElse("2.5.3").get()

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    ndk {
      abiFilters += listOf("arm64-v8a")
    }
  }

  signingConfigs {
    create("release") {
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
      isMinifyEnabled = true
      isShrinkResources = true
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
    jniLibs.useLegacyPackaging = true
  }
}

// Never allow a production release artifact to be emitted unsigned.
// Debug/test builds remain usable without production signing credentials.
gradle.taskGraph.whenReady {
  val releaseRequested = allTasks.any { task ->
    task.name.contains("Release", ignoreCase = true) &&
      task.name.matches(Regex("(assemble|bundle|package|sign|validate).*", RegexOption.IGNORE_CASE))
  }
  if (releaseRequested) {
    val keystorePath = System.getenv("KEYSTORE_PATH").orEmpty().trim()
    val storePass = System.getenv("STORE_PASSWORD").orEmpty().trim()
    val keyPass = System.getenv("KEY_PASSWORD").orEmpty().trim()
    require(keystorePath.isNotEmpty() && storePass.isNotEmpty() && keyPass.isNotEmpty()) {
      "Production Android release signing is not configured. Set KEYSTORE_PATH, STORE_PASSWORD, and KEY_PASSWORD before building a release APK/AAB."
    }
    require(file(keystorePath).isFile) {
      "Production Android signing keystore not found at KEYSTORE_PATH. Refusing to create an unsigned release artifact."
    }
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
  implementation(libs.androidx.work.runtime)
  implementation(libs.zxing.core)
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)

  testImplementation(libs.androidx.core)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  "ksp"(libs.androidx.room.compiler)

  debugImplementation(libs.androidx.compose.ui.tooling)
}
