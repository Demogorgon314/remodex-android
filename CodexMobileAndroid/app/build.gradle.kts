plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

fun propOrEnv(propName: String, envName: String): String? {
    val propValue = providers.gradleProperty(propName).orNull?.trim()
    if (!propValue.isNullOrEmpty()) {
        return propValue
    }

    val envValue = providers.environmentVariable(envName).orNull?.trim()
    if (!envValue.isNullOrEmpty()) {
        return envValue
    }

    return null
}

val configuredVersionCode = propOrEnv("remodexAndroidVersionCode", "ANDROID_VERSION_CODE")?.toInt() ?: 1
val configuredVersionName = propOrEnv("remodexAndroidVersionName", "ANDROID_VERSION_NAME") ?: "1.0"

val releaseKeystorePath = propOrEnv("remodexAndroidKeystorePath", "ANDROID_KEYSTORE_PATH")
val releaseKeystorePassword = propOrEnv("remodexAndroidKeystorePassword", "ANDROID_KEYSTORE_PASSWORD")
val releaseKeyAlias = propOrEnv("remodexAndroidKeyAlias", "ANDROID_KEY_ALIAS")
val releaseKeyPassword = propOrEnv("remodexAndroidKeyPassword", "ANDROID_KEY_PASSWORD") ?: releaseKeystorePassword
val hasReleaseSigning = listOf(
    releaseKeystorePath,
    releaseKeystorePassword,
    releaseKeyAlias,
).all { !it.isNullOrBlank() }

android {
    namespace = "com.emanueledipietro.remodex"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.emanueledipietro.remodex"
        minSdk = 26
        targetSdk = 36
        versionCode = configuredVersionCode
        versionName = configuredVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(requireNotNull(releaseKeystorePath))
                storePassword = requireNotNull(releaseKeystorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

val requestedTasks = gradle.startParameter.taskNames
val releasePackagingTaskRequested = requestedTasks.any { taskName ->
    when (taskName.substringAfterLast(':').lowercase()) {
        "assemblerelease", "bundlerelease", "packagerelease", "validatesigningrelease" -> true
        else -> false
    }
}

if (releasePackagingTaskRequested && !hasReleaseSigning) {
    throw GradleException(
        "Release signing is required for release tasks. Set remodexAndroidKeystorePath/remodexAndroidKeystorePassword/" +
            "remodexAndroidKeyAlias/remodexAndroidKeyPassword or the ANDROID_KEYSTORE_PATH/" +
            "ANDROID_KEYSTORE_PASSWORD/ANDROID_KEY_ALIAS/ANDROID_KEY_PASSWORD environment variables."
    )
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.androidx.security.crypto.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.zxing.android.embedded)
    implementation(libs.bouncycastle.bcprov)
    implementation(libs.coil.compose)
    implementation(libs.photo.view)
    implementation(libs.markwon.core)
    implementation(libs.markwon.ext.strikethrough)
    implementation(libs.markwon.ext.tables)
    implementation(libs.markwon.syntax.highlight) {
        exclude(group = "org.jetbrains", module = "annotations-java5")
    }
    implementation(libs.prism4j) {
        exclude(group = "org.jetbrains", module = "annotations-java5")
    }
    compileOnly(libs.prism4j.bundler) {
        exclude(group = "org.jetbrains", module = "annotations-java5")
    }
    annotationProcessor(libs.prism4j.bundler)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
