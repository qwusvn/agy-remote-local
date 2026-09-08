import java.io.File

val currentVersionName = "1.0.6"

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.agyremote"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.example.agyremote"
        minSdk = 24
        targetSdk = 36
        versionCode = 7
        versionName = currentVersionName
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = true
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.extended)

  // WebKit & Custom WebView
  implementation(libs.androidx.webkit)

  // DataStore & Persistence
  implementation(libs.androidx.datastore.preferences)

  // Networking & WebSocket
  implementation(libs.okhttp)
  implementation(libs.okhttp.logging)
  implementation(libs.kotlinx.serialization.json)

  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)
}

// Tự động lưu duy nhất 1 bản APK vào D:/apk theo Điều luật 7 (v1.7.0)
val copyApkTask = tasks.register("copyApkToTargetDir") {
    val buildDir = layout.buildDirectory
    doLast {
        val targetDir = File("D:/apk")
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        val debugApk = buildDir.file("outputs/apk/debug/app-debug.apk").get().asFile
        if (debugApk.exists()) {
            // Xóa tất cả các bản agy-remote*.apk cũ trong D:/apk để bảo đảm Rule 7: Duy nhất 1 bản APK
            val existing = targetDir.listFiles()
            if (existing != null) {
                for (f in existing) {
                    if (f.name.startsWith("agy-remote") && f.name.endsWith(".apk")) {
                        f.delete()
                    }
                }
            }
            // Xóa file trùng lặp app-debug.apk nếu có
            val duplicate = File(targetDir, "app-debug.apk")
            if (duplicate.exists()) {
                duplicate.delete()
            }

            val dest = File(targetDir, "agy-remote-debug $currentVersionName.apk")
            debugApk.copyTo(dest, overwrite = true)
        }
    }
}

tasks.matching { it.name.startsWith("assemble") }.configureEach {
    finalizedBy(copyApkTask)
}
