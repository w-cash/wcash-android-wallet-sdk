plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("zcash-sdk.android-conventions")
}

android {
    namespace = "cash.w.sdk.consumertest"

    defaultConfig {
        applicationId = "cash.w.sdk.consumertest"
        versionCode = 1
        versionName = "1"
        testProguardFiles("proguard-test-project.txt")
    }

    // Instrumentation must execute the whole-program R8 output, not an unminified debug build.
    testBuildType = "release"

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                // Wcash wallet releases preserve semantic visibility boundaries. The optimized
                // Android defaults enable global access widening, which no library consumer rule
                // can reliably negate for a particular class.
                getDefaultProguardFile("proguard-android.txt"),
                "proguard-project.txt"
            )
        }
    }
}

dependencies {
    implementation(project(":wcash-android-sdk"))
    implementation(libs.kotlin.stdlib)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    // The minified test APK runs in its own class loader. Package the Kotlin runtime there
    // explicitly rather than relying on classes retained by the separately optimized app APK.
    androidTestImplementation(libs.kotlin.stdlib)
    androidTestImplementation(libs.kotlin.test)
}
