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
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.annotation:annotation:1.9.1")

    // ===== KOTLIN COROUTINES (ESSENTIAL for async operations) =====
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // ===== UI & GRAPHICS =====
    implementation("androidx.recyclerview:recyclerview:1.4.0") // For page recycling if needed
    implementation("androidx.customview:customview:1.2.0") // For custom view support

    // ===== LIFECYCLE (for memory management) =====
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.2")
    implementation("androidx.lifecycle:lifecycle-common:2.9.2")

    // ===== COLLECTION UTILITIES =====
    implementation("androidx.collection:collection-ktx:1.5.0") // For LruCache and collections

    // ===== OPTIONAL: For advanced bitmap operations =====
    implementation("androidx.palette:palette-ktx:1.0.0") // For color extraction if needed

    // ===== NO NETWORK DEPENDENCIES =====
    // Không cần OkHttp3 vì library chỉ xử lý local files, URIs, streams
    // Network loading sẽ do consumer app handle

    // ===== TESTING DEPENDENCIES =====
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.5.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("androidx.test.ext:junit:1.1.5")
    testImplementation("org.robolectric:robolectric:4.10.3")

    // Android Instrumented Tests
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")

    // ===== BENCHMARK DEPENDENCIES (for performance module) =====
    androidTestImplementation("androidx.benchmark:benchmark-junit4:1.4.0")

    // ===== MEMORY LEAK DETECTION (debug builds only) =====
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")

//    implementation(libs.androidx.core.ktx)
//    implementation(libs.androidx.appcompat)
//    implementation(libs.material)
//    testImplementation(libs.junit)
//    androidTestImplementation(libs.androidx.junit)
//    androidTestImplementation(libs.androidx.espresso.core)
}