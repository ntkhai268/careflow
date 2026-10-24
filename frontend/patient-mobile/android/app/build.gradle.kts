import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

val releaseKeyPath = providers.environmentVariable("CAREFLOW_ANDROID_KEY_PROPERTIES").orNull
val releaseKeys = Properties()
if (releaseKeyPath != null) {
    val keyFile = file(releaseKeyPath)
    require(keyFile.isFile) { "CAREFLOW_ANDROID_KEY_PROPERTIES must point to a private signing properties file" }
    keyFile.inputStream().use { releaseKeys.load(it) }
    for (key in listOf("storeFile", "storePassword", "keyAlias", "keyPassword")) {
        require(!releaseKeys.getProperty(key).isNullOrBlank()) { "Missing Android signing property: $key" }
    }
}
if (gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }) {
    require(releaseKeyPath != null) { "Release requires CAREFLOW_ANDROID_KEY_PROPERTIES; debug signing is not allowed" }
}

android {
    namespace = "com.careflow.careflow_patient"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    defaultConfig {
        // TODO: Specify your own unique Application ID (https://developer.android.com/studio/build/application-id.html).
        applicationId = "com.careflow.careflow_patient"
        // You can update the following values to match your application needs.
        // For more information, see: https://flutter.dev/to/review-gradle-config.
        minSdk = flutter.minSdkVersion
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    signingConfigs {
        if (releaseKeyPath != null) {
            create("release") {
                storeFile = file(releaseKeys.getProperty("storeFile"))
                storePassword = releaseKeys.getProperty("storePassword")
                keyAlias = releaseKeys.getProperty("keyAlias")
                keyPassword = releaseKeys.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

flutter {
    source = "../.."
}

dependencies {
    // Statically bundled on-device recognizer, not the Google Play model-download variant.
    implementation("com.google.mlkit:text-recognition:16.0.1")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    constraints {
        // Flutter integration_test pulls 3.2.0, whose duplicate namespaces fail
        // manifest validation with this project's Android Gradle plugin.
        implementation("androidx.test.espresso:espresso-core:3.6.1")
        implementation("androidx.test.espresso:espresso-idling-resource:3.6.1")
    }
}
