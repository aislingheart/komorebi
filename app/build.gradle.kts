import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.aboutlibraries)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "androidx.compose.foundation.ExperimentalFoundationApi",
            "androidx.compose.ui.text.ExperimentalTextApi",
            "androidx.compose.foundation.layout.ExperimentalLayoutApi",
        )
    }
}

android {
    namespace = "com.sameerasw.essentials"
    compileSdk = 37
    compileSdkMinor = 1

    androidResources {
        localeFilters +=
            listOf(
                "en",
                "ach",
                "af",
                "ar",
                "bn",
                "bn-rBD",
                "ca",
                "cs",
                "da",
                "de",
                "el",
                "es",
                "fi",
                "fil",
                "fil-rPH",
                "fr",
                "he",
                "hi",
                "hi-rIN",
                "hu",
                "id",
                "in",
                "in-rID",
                "it",
                "iw",
                "ja",
                "kk",
                "kk-rKZ",
                "ko",
                "ml",
                "ml-rIN",
                "ne",
                "ne-rNP",
                "nl",
                "no",
                "pl",
                "pt",
                "pt-rBR",
                "pt-rPT",
                "ro",
                "ru",
                "si",
                "sk",
                "sk-rSK",
                "sr",
                "sv",
                "ta",
                "ta-rIN",
                "tr",
                "uk",
                "vi",
                "zh",
                "zh-rCN",
                "zh-rTW",
            )
    }

    defaultConfig {
        applicationId = "com.aislingheart.komorebi"
        minSdk = 26
        targetSdk = 37
        versionCode = 84
        versionName = "0.5"

        val whatsNewCounter = 10
        buildConfigField("int", "WHATS_NEW_COUNTER", whatsNewCounter.toString())
        buildConfigField("int", "REQUIRED_WEAR_VERSION_CODE", "8")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        val signingPropsFile = File(System.getProperty("user.home"), ".android/komorebi-signing.properties")
        if (signingPropsFile.exists()) {
            val props = Properties().apply { signingPropsFile.inputStream().use { load(it) } }
            create("release") {
                storeFile = File(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
//        optimized dev build
//           debug {
//              isMinifyEnabled = true
//              isShrinkResources = true
//              isDebuggable = false
//              proguardFiles(
//                  getDefaultProguardFile("proguard-android-optimize.txt"),
//                  "proguard-rules.pro"
//              )
//           }
//        end

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfigs.findByName("release")?.let { signingConfig = it }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    compileOnly(project(":stub"))
    implementation(project(":island-sdk"))

    implementation(libs.aboutlibraries.core)
    implementation(libs.aboutlibraries.compose.m3)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigationevent)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.material3.adaptive)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.compose.material3.v150alpha24)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.palette)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.genai.schema)
    implementation(libs.material)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.foundation.layout)
    implementation(libs.androidx.foundation)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation("com.github.d4rken-org.porter-api:shizuku-bridge:+")
    implementation(libs.api.v1315)
    implementation(libs.hiddenapibypass)
    implementation(libs.gson.v2140)
    implementation(libs.gson)
    implementation(libs.reorderable)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(kotlin("reflect"))
    implementation(libs.symspellkt)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.jsoup)
    implementation(libs.sentry.android)
    implementation(libs.androidx.graphics.shapes)
    implementation(libs.autoupdater)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.ui)
    implementation(libs.lottie.compose)
    implementation(libs.mlkit.genai.prompt)
    ksp(libs.mlkit.genai.schema.compiler)
    implementation(libs.androidx.appfunctions)
    ksp(libs.androidx.appfunctions.compiler)
    implementation(libs.zxing.core)
    implementation(libs.play.services.code.scanner)
    implementation(libs.quickjs.kt)
    implementation(libs.okhttp)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.litert)
    implementation(libs.commons.compress)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
