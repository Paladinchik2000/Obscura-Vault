// A login form in a package of its own, for the autofill end-to-end tests.
//
// The form in androidTest lives in com.obscura.test, and a package and its instrumentation always
// see each other, so tests using it cannot notice when Obscura is blind to the app it fills. This
// app has no instrumentation and no relation to Obscura: it is as visible to the service as any
// app from the store would be. The APKs are packed into the androidTest assets and installed by
// the test itself, on the device the test runs on.
plugins {
  alias(libs.plugins.android.application)
}

android {
  namespace = "com.obscura.autofilltarget"
  compileSdk { version = release(37) }

  defaultConfig {
    applicationId = "com.obscura.autofilltarget"
    minSdk = 26
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"
  }

  // Two copies of the same form: one with a launcher icon, which Obscura's <queries> makes
  // visible, and one without, which stays invisible and must still get the manual choice.
  flavorDimensions += "icon"
  productFlavors {
    create("launchable") { dimension = "icon" }
    create("hidden") {
      dimension = "icon"
      applicationIdSuffix = ".hidden"
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
}
