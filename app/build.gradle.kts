plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}
android {
    namespace = "com.example.multiplicationquiz"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.example.multiplicationquiz"
        minSdk = 23
        targetSdk = 35
        versionCode = 2
        versionName = "2.0-online"
    }
}
dependencies {
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-database")
    implementation("com.google.firebase:firebase-storage")
}
