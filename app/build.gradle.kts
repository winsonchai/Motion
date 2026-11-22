import java.net.URI
import java.security.MessageDigest

plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
val previewKey = providers.environmentVariable("MOTION_PREVIEW_KEYSTORE").orNull
android {
    namespace = "com.winsonchai.motion"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.winsonchai.motion"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    signingConfigs {
        if (!previewKey.isNullOrBlank()) getByName("debug") {
            storeFile = file(previewKey)
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
}

// Build-time download only. The model is bundled in the APK; no runtime network access.
val modelFile = layout.projectDirectory.file("src/main/assets/pose_landmarker_lite.task")
val preparePoseModel by tasks.registering {
    outputs.file(modelFile)
    outputs.upToDateWhen { false } // Recheck model integrity on every build.
    doLast {
        val destination = modelFile.asFile
        fun digest(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
        if (destination.exists() && digest(destination) == "59929e1d1ee95287735ddd833b19cf4ac46d29bc7afddbbf6753c459690d574a") return@doLast
        destination.parentFile.mkdirs()
        val temporary = File(destination.path + ".download")
        try {
            URI("https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task")
                .toURL().openConnection().apply { connectTimeout = 30000; readTimeout = 120000 }
                .getInputStream().use { input -> temporary.outputStream().use { input.copyTo(it) } }
            val digest = MessageDigest.getInstance("SHA-256").digest(temporary.readBytes())
                .joinToString("") { "%02x".format(it) }
            check(digest == "59929e1d1ee95287735ddd833b19cf4ac46d29bc7afddbbf6753c459690d574a") { "Pose model checksum mismatch: $digest" }
            check(temporary.renameTo(destination)) { "Could not install pose model" }
        } finally { temporary.delete() }
    }
}
tasks.named("preBuild").configure { dependsOn(preparePoseModel) }
dependencies {
    implementation(project(":core"))
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("com.google.mediapipe:tasks-vision:0.10.32")
}
