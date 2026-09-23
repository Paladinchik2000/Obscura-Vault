plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
}

// Built from :autofilltarget. It is a separate app on purpose: a form in com.obscura.test is always
// visible to Obscura through the instrumentation, so it cannot catch package-visibility bugs.
val autofillTargetApks = layout.buildDirectory.dir("generated/autofillTargetApks").get().asFile
val packAutofillTargetApks = tasks.register<Sync>("packAutofillTargetApks") {
  dependsOn(":autofilltarget:assembleLaunchableDebug", ":autofilltarget:assembleHiddenDebug")
  from(rootProject.layout.projectDirectory.dir("autofilltarget/build/outputs/apk")) {
    include("launchable/debug/*.apk", "hidden/debug/*.apk")
    eachFile { path = "autofilltarget-${file.parentFile.parentFile.name}.apk" }
  }
  includeEmptyDirs = false
  into(autofillTargetApks)
}
tasks.matching { it.name.endsWith("AndroidTestAssets") }.configureEach { dependsOn(packAutofillTargetApks) }

android {
  namespace = "com.obscura"
  compileSdk { version = release(37) }

  defaultConfig {
    applicationId = "com.obscura"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true 
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
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
  // Exported Room schemas: needed to write real migrations and to test them.
  sourceSets["androidTest"].assets.srcDir("$projectDir/schemas")
  // The autofill target app, packed into the test APK and installed by the test itself.
  sourceSets["androidTest"].assets.srcDir(autofillTargetApks)
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
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
  implementation(libs.androidx.biometric)
  implementation(libs.sqlcipher.android)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation("androidx.autofill:autofill:1.1.0")
  implementation("androidx.fragment:fragment-ktx:1.8.5")
  implementation("androidx.biometric:biometric-ktx:1.2.0-alpha05")
  implementation("androidx.navigation:navigation-compose:2.8.5")
  implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
  implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
  implementation("androidx.compose.material:material-icons-extended")

  implementation(libs.androidx.lifecycle.process)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.room.testing)
  androidTestImplementation(libs.androidx.test.uiautomator)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
}
