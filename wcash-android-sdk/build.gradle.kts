import java.io.File
import java.util.zip.ZipFile

plugins {
    id("org.mozilla.rust-android-gradle.rust-android")
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("zcash-sdk.android-conventions")
}

base {
    archivesName.set("wcash-android-sdk")
}

android {
    namespace = "cash.w.sdk"

    defaultConfig {
        consumerProguardFiles("proguard-consumer.txt")
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
        }
        getByName("release") {
            isMinifyEnabled = project.property("IS_MINIFY_SDK_ENABLED").toString().toBoolean()
            proguardFiles(
                getDefaultProguardFile("proguard-android.txt"),
                File("proguard-project.txt")
            )
        }
    }
}

cargo {
    module = "."
    libname = "wcashwalletsdk"
    targets = listOf("arm", "arm64", "x86", "x86_64")
    val minSdkVersion = project.property("ANDROID_MIN_SDK_VERSION").toString().toInt()
    apiLevels = mapOf(
        "arm" to minSdkVersion,
        "arm64" to minSdkVersion,
        "x86" to minSdkVersion,
        "x86_64" to minSdkVersion
    )
    profile = "release"
    // Android ABI builds must use the reviewed dependency graph in Cargo.lock. Without this,
    // cargo-apk can silently resolve a newer transitive dependency than host-side CI validated.
    extraCargoBuildArguments = listOf("--locked")
    prebuiltToolchains = true
    pythonCommand =
        providers
            .environmentVariable("RUST_ANDROID_GRADLE_PYTHON_COMMAND")
            .orElse("python3")
            .get()

    val userHome = System.getProperty("user.home")
    val cargoHome =
        providers
            .environmentVariable("CARGO_HOME")
            .orElse(File(userHome, ".cargo").absolutePath)
            .get()
    val inheritedEncodedRustFlags =
        providers.environmentVariable("CARGO_ENCODED_RUSTFLAGS").orNull
    val remapFlags =
        listOf(
            "--remap-path-prefix=${rootProject.projectDir.absolutePath}=/wcash-workspace",
            "--remap-path-prefix=$cargoHome=/cargo",
            "--remap-path-prefix=$userHome=/build-home"
        ).joinToString("\u001f")
    val encodedRustFlags =
        if (inheritedEncodedRustFlags.isNullOrBlank()) {
            remapFlags
        } else {
            "$inheritedEncodedRustFlags\u001f$remapFlags"
        }
    exec = { spec, _ ->
        spec.environment["RUST_ANDROID_GRADLE_CC_LINK_ARG"] = "-Wl,-z,max-page-size=16384"
        spec.environment["CARGO_ENCODED_RUSTFLAGS"] = encodedRustFlags
    }

    File(System.getProperty("user.home"), ".cargo/bin").let { cargoBin ->
        val rustc = File(cargoBin, "rustc")
        val cargo = File(cargoBin, "cargo")
        if (rustc.exists() && cargo.exists()) {
            rustcCommand = rustc.absolutePath
            cargoCommand = cargo.absolutePath
        }
    }
}

// rust-android-gradle does not wire its generated JNI directory into modern Gradle versions.
tasks.configureEach {
    if (name.matches("^merge.+JniLibFolders$".toRegex())) {
        dependsOn("cargoBuildArm", "cargoBuildArm64", "cargoBuildX86", "cargoBuildX86_64")
        inputs.dir(layout.buildDirectory.dir("rustJniLibs/android").get().asFile)
    }
}

dependencies {
    implementation(libs.androidx.annotation)
    implementation(libs.kotlin.stdlib)

    testImplementation(libs.bundles.junit)
    testImplementation(libs.kotlin.test)

    androidTestImplementation(libs.androidx.multidex)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlin.test)
}

val isSdkMinificationEnabled =
    providers.gradleProperty("IS_MINIFY_SDK_ENABLED").map(String::toBoolean).orElse(false)
val releaseAar = layout.buildDirectory.file("outputs/aar/wcash-android-sdk-release.aar")
val releaseMapping = layout.buildDirectory.file("outputs/mapping/release/mapping.txt")
val localBuildPaths =
    listOfNotNull(
        rootProject.projectDir.absolutePath,
        System.getProperty("user.home"),
        providers.environmentVariable("CARGO_HOME").orNull,
        "/Users/",
        "/home/runner/work/",
        "C:\\Users\\"
    ).flatMap { path -> listOf(path, path.replace('\\', '/')) }
        .filter(String::isNotBlank)
        .distinct()

val verifyReleaseAarSeedApi =
    tasks.register<Exec>("verifyReleaseAarSeedApi") {
        group = "verification"
        description = "Checks the R8 release AAR for raw Wcash seed API leaks."
        dependsOn("bundleReleaseAar")
        inputs.file(releaseAar)
        inputs.file(releaseMapping)
        inputs.property("isSdkMinificationEnabled", isSdkMinificationEnabled)
        environment("JAVA_HOME", System.getProperty("java.home"))
        commandLine(
            "bash",
            layout.projectDirectory.file("scripts/verify-release-aar-seed-api.sh").asFile.absolutePath,
            releaseAar.get().asFile.absolutePath
        )

        doFirst {
            check(isSdkMinificationEnabled.get()) {
                "verifyReleaseAarSeedApi must run with -PIS_MINIFY_SDK_ENABLED=true"
            }
            check(releaseMapping.get().asFile.isFile) {
                "R8 mapping is missing for the release AAR"
            }
        }
    }

val verifyReleaseAarNativePaths =
    tasks.register("verifyReleaseAarNativePaths") {
        group = "verification"
        description = "Checks native release libraries for private build-system paths."
        dependsOn("bundleReleaseAar")
        inputs.file(releaseAar)
        inputs.property("forbiddenBuildPaths", localBuildPaths)

        doLast {
            fun ByteArray.containsBytes(candidate: ByteArray): Boolean =
                candidate.isNotEmpty() &&
                    candidate.size <= size &&
                    (0..size - candidate.size).any { offset ->
                        candidate.indices.all { index -> this[offset + index] == candidate[index] }
                    }

            val nativeLibraries = mutableMapOf<String, ByteArray>()
            ZipFile(releaseAar.get().asFile).use { aar ->
                val entries = aar.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.name.matches("jni/[^/]+/libwcashwalletsdk\\.so".toRegex())) {
                        nativeLibraries[entry.name] = aar.getInputStream(entry).readBytes()
                    }
                }
            }

            val expectedLibraries =
                setOf(
                    "jni/arm64-v8a/libwcashwalletsdk.so",
                    "jni/armeabi-v7a/libwcashwalletsdk.so",
                    "jni/x86/libwcashwalletsdk.so",
                    "jni/x86_64/libwcashwalletsdk.so"
                )
            check(nativeLibraries.keys == expectedLibraries) {
                "Release AAR native libraries were ${nativeLibraries.keys.sorted()}"
            }

            nativeLibraries.forEach { (entryName, binary) ->
                localBuildPaths.forEach { privatePath ->
                    check(!binary.containsBytes(privatePath.encodeToByteArray())) {
                        "$entryName contains a private build path matching '$privatePath'"
                    }
                }
            }
        }
    }

val verifyReleaseAarBoundaries =
    tasks.register("verifyReleaseAarBoundaries") {
        group = "verification"
        description = "Verifies minified seed visibility and native path redaction."
        dependsOn(verifyReleaseAarSeedApi, verifyReleaseAarNativePaths)
    }

if (isSdkMinificationEnabled.get()) {
    tasks.named("check").configure { dependsOn(verifyReleaseAarBoundaries) }
}
