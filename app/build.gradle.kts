plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.ksp)
  alias(libs.plugins.room)
}

val keystorePath: String? = (findProperty("keeltv.keystore") as String?) ?: System.getenv("KEELTV_KEYSTORE")

val buildNumber: Int? = System.getenv("KEELTV_BUILD_NUMBER")?.toInt()

val releasesRepo: String? = System.getenv("GITHUB_REPOSITORY")

android {
  namespace = "com.keeltv.app"
  compileSdk = 37

  defaultConfig {
    applicationId = "com.keeltv.app"
    minSdk = 26
    targetSdk = 36
    versionCode = buildNumber ?: 1
    versionName = "0.0.${buildNumber ?: 0}"
    buildConfigField("String", "RELEASES_REPO", releasesRepo?.let { "\"$it\"" } ?: "null")
  }

  signingConfigs {
    if (keystorePath != null) {
      create("release") {
        storeFile = file(keystorePath)
        storePassword = (findProperty("keeltv.storePassword") as String?) ?: System.getenv("KEELTV_STORE_PASSWORD")
        keyAlias = (findProperty("keeltv.keyAlias") as String?) ?: System.getenv("KEELTV_KEY_ALIAS")
        keyPassword = (findProperty("keeltv.keyPassword") as String?) ?: System.getenv("KEELTV_KEY_PASSWORD")
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
    }
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  packaging {
    resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
  }
}

kotlin {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
  }
}

room {
  schemaDirectory("$projectDir/schemas")
}

dependencies {
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  implementation(platform(libs.compose.bom))
  implementation(libs.compose.foundation)
  implementation(libs.compose.ui)
  implementation(libs.compose.animation)
  implementation(libs.compose.material.icons)
  implementation(libs.compose.ui.tooling.preview)
  debugImplementation(libs.compose.ui.tooling)

  implementation(libs.androidx.tv.material)
  implementation(libs.androidx.tvprovider)
  implementation(libs.androidx.navigation.compose)

  implementation(libs.media3.exoplayer)
  implementation(libs.media3.exoplayer.hls)
  implementation(libs.media3.ui)
  implementation(libs.media3.datasource.okhttp)
  implementation(libs.media3.session)
  implementation(files(rootProject.layout.projectDirectory.file("libs/media3-decoder-ffmpeg-1.11.1.aar")))

  implementation(libs.room.runtime)
  implementation(libs.room.ktx)
  implementation(libs.room.paging)
  ksp(libs.room.compiler)

  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.paging.runtime)
  implementation(libs.androidx.paging.compose)

  implementation(libs.coil.compose)
  implementation(libs.coil.network.okhttp)

  implementation(libs.okhttp)
  implementation(libs.moshi)
  implementation(libs.kotlinx.coroutines.android)
}
