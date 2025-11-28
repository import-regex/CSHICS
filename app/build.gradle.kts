plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Remove any Compose-specific plugins if they were added, though usually not needed here
}

android {
    namespace = "com.example.cshics" // Or your actual namespace
    compileSdk = 34 // Keep this modern (e.g., 33 or 34)

    defaultConfig {
        applicationId = "com.example.CSHICS"
        minSdk = 16
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        multiDexEnabled = true // Keep this, good for larger apps even if method count drops
    }

    buildTypes {
        release {
            isMinifyEnabled = false // Consider true for actual releases
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        // Disable Compose
        compose = false // <--- IMPORTANT
        // Enable ViewBinding (optional but recommended for XML layouts)
        viewBinding = true // <--- RECOMMENDED
    }
    
}

dependencies {
    // Core Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.0") // Or latest compatible Kotlin version
    implementation("androidx.core:core-ktx:1.9.0") // Use a version known to be compatible with older APIs

    // AppCompat for backward compatibility of UI elements (ActionBar, themes)
    implementation("androidx.appcompat:appcompat:1.6.1") // Generally good with minSdk 17

    // Material Design Components (for Views, not Compose)
    // Ensure this version's minSdk is compatible or use an older one if necessary.
    // 1.10.0 should generally be okay but double-check if issues persist.
    implementation("com.google.android.material:material:1.10.0")

    // ConstraintLayout (if you plan to use it for XML layouts)
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Multidex support library (still needed for minSdk < 21)
    implementation("androidx.multidex:multidex:2.0.1")

    // Testing libraries (can usually stay as they are)
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    implementation("org.nanohttpd:nanohttpd:2.3.1")
}