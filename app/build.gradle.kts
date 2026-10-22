plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "dev.raamiz.assistantdiagnostic"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.raamiz.assistantdiagnostic"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1-diagnostic"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencyLocking { lockAllConfigurations() }
