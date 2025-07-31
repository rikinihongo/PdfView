plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("kotlin-parcelize") // For Parcelable data classes
    id("maven-publish") // For library publishing
}

android {
    namespace = "com.sonpxp.pdfviewer"
    compileSdk = 36

    defaultConfig {
        minSdk = 23

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        // Library version
        buildConfigField("String", "VERSION_NAME", "\"1.0.0\"")
        buildConfigField("int", "VERSION_CODE", "1")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-Xjvm-default=all"
        )
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true // For internal UI components
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // ===== CORE ANDROID DEPENDENCIES =====
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.annotation)
    implementation(libs.material)

    // ===== KOTLIN COROUTINES (ESSENTIAL for async operations) =====
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // ===== UI & GRAPHICS =====
    implementation(libs.androidx.recyclerview) // For page recycling if needed
    implementation(libs.androidx.customview) // For custom view support

    // ===== LIFECYCLE (for memory management) =====
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.common)

    // ===== COLLECTION UTILITIES =====
    implementation(libs.androidx.collection.ktx) // For LruCache and collections

    // ===== OPTIONAL: For advanced bitmap operations =====
    implementation(libs.androidx.palette.ktx) // For color extraction if needed

    // ===== NO NETWORK DEPENDENCIES =====
    // Không cần OkHttp3 vì library chỉ xử lý local files, URIs, streams
    // Network loading sẽ do consumer app handle

    // ===== TESTING DEPENDENCIES =====
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.org.jetbrains.kotlinx.kotlinx.coroutines.test)
    testImplementation(libs.androidx.core)
    testImplementation(libs.androidx.junit.v115)
    testImplementation(libs.robolectric)

    // Android Instrumented Tests
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.runner)
    androidTestImplementation(libs.androidx.rules)
    androidTestImplementation(libs.org.jetbrains.kotlinx.kotlinx.coroutines.test)

    // ===== BENCHMARK DEPENDENCIES (for performance module) =====
    androidTestImplementation(libs.androidx.benchmark.junit4)

    // ===== MEMORY LEAK DETECTION (debug builds only) =====
    debugImplementation(libs.leakcanary.android)


}