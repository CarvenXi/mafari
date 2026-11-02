import java.io.File
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Servo AAR：默认从 Servo 官方 GitHub 最新稳定版 Release 下载。
// 可通过 -PservoAarUrl=... 或环境变量 SERVO_AAR_URL 覆盖下载地址。
// 默认同时下载同名 .sha256 文件并校验，避免把错误/不完整文件交给 Kotlin 编译器。
val servoAarUrl = providers.gradleProperty("servoAarUrl")
    .orElse(providers.environmentVariable("SERVO_AAR_URL"))
    .orElse("https://github.com/servo/servo/releases/latest/download/servo-aarch64-android.aar")
val servoAarSha256Url = providers.gradleProperty("servoAarSha256Url")
    .orElse(providers.environmentVariable("SERVO_AAR_SHA256_URL"))
val forceServoAarRefresh = providers.gradleProperty("refreshServoAar")
    .map { it.equals("true", ignoreCase = true) }
    .orElse(false)
val servoAarFile = layout.buildDirectory.file("generated/servo/servo-aarch64-android.aar")

val downloadServoAar by tasks.registering {
    val outputAar = servoAarFile.get().asFile
    inputs.property("servoAarUrl", servoAarUrl)
    outputs.file(servoAarFile)
    outputs.upToDateWhen { outputAar.isFile && !forceServoAarRefresh.get() }

    doLast {
        val downloadUrl = servoAarUrl.get()
        val checksumUrl = servoAarSha256Url.orNull?.takeIf { it.isNotBlank() }
            ?: "$downloadUrl.sha256"
        val tempAar = File(outputAar.parentFile, outputAar.name + ".part")
        outputAar.parentFile.mkdirs()

        try {
            logger.lifecycle("Downloading Servo AAR: $downloadUrl")
            URL(downloadUrl).openConnection().apply {
                connectTimeout = 30_000
                readTimeout = 180_000
                setRequestProperty("User-Agent", "Mafari-Gradle")
            }.getInputStream().use { input ->
                tempAar.outputStream().use { output -> input.copyTo(output) }
            }

            if (!tempAar.isFile || tempAar.length() < 1_000_000L) {
                throw GradleException("Servo AAR 下载失败：文件不存在或体积异常。")
            }
            ZipFile(tempAar).use { aar ->
                if (aar.getEntry("AndroidManifest.xml") == null || aar.getEntry("classes.jar") == null) {
                    throw GradleException("下载文件不是有效的 Android AAR：缺少 AndroidManifest.xml 或 classes.jar。")
                }
            }

            val checksumText = URL(checksumUrl).openConnection().apply {
                connectTimeout = 30_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "Mafari-Gradle")
            }.getInputStream().bufferedReader().use { it.readText() }
            val expectedSha256 = Regex("(?i)\\b[0-9a-f]{64}\\b")
                .find(checksumText)?.value
                ?: throw GradleException("无法从校验文件中读取 SHA-256：$checksumUrl")
            val actualSha256 = MessageDigest.getInstance("SHA-256")
                .digest(tempAar.readBytes())
                .joinToString("") { "%02x".format(it) }
            if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
                throw GradleException("Servo AAR SHA-256 校验失败。expected=$expectedSha256 actual=$actualSha256")
            }

            Files.move(tempAar.toPath(), outputAar.toPath(), StandardCopyOption.REPLACE_EXISTING)
            logger.lifecycle("Servo AAR ready: ${outputAar.absolutePath} ($actualSha256)")
        } catch (error: Exception) {
            tempAar.delete()
            throw GradleException("无法准备 Servo AAR。请检查网络、下载 URL 和 SHA-256 地址。", error)
        }
    }
}

// 确保直接执行 assemble / compile Kotlin 等构建任务时，也会先准备 AAR。
tasks.configureEach {
    if (name == "preBuild" || name.startsWith("compile") ||
        name.startsWith("assemble") || name.startsWith("bundle")) {
        dependsOn(downloadServoAar)
    }
}

android {
    namespace = "dev.dsh.miuixbrowser"
    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "dev.dsh.miuixbrowser"
        minSdk = 33
        targetSdk = 36
        versionCode = 95
        versionName = "v1.0.11-dev"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        val keystorePath = System.getenv("MAFARI_KEYSTORE")
        if (keystorePath != null && rootProject.file(keystorePath).exists()) {
            create("release") {
                storeFile = rootProject.file(keystorePath)
                storePassword = System.getenv("MAFARI_STORE_PASS")
                keyAlias = System.getenv("MAFARI_KEY_ALIAS")
                keyPassword = System.getenv("MAFARI_KEY_PASS")
            }
        }
    }

    buildTypes {
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = true
            isShrinkResources = true
            optimization {
                enable = true
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}


dependencies {
    val miuix = "0.9.4"
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:$miuix")
    implementation("top.yukonga.miuix.kmp:miuix-preference-android:$miuix")
    implementation("top.yukonga.miuix.kmp:miuix-icons-android:$miuix")
    implementation("top.yukonga.miuix.kmp:miuix-squircle-android:$miuix")
    implementation("top.yukonga.miuix.kmp:miuix-blur-android:$miuix")

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.webkit:webkit:1.14.0")
    implementation("androidx.browser:browser:1.8.0")

    // Servo Android arm64 engine (downloaded and SHA-256 verified before compilation).
    implementation(files(servoAarFile))
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
