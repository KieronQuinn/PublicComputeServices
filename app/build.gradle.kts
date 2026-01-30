import com.google.protobuf.gradle.id
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.protobuf)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.sekret)
    alias(libs.plugins.aboutLibraries)
    alias(libs.plugins.parcelize)
}

private fun getLocalProperties(): Properties? {
    var properties: Properties? = Properties()
    properties?.setProperty("keyAlias", "")
    properties?.setProperty("keyPassword", "")
    properties?.setProperty("storeFile", "")
    properties?.setProperty("storePassword", "")
    properties?.setProperty("keyHash", "")
    try {
        val propertiesFile = rootProject.file("local.properties")
        if (propertiesFile.exists()) {
            properties?.load(FileInputStream(propertiesFile))
        }
    } catch (ignored: Exception) {
        properties = null
        println("Unable to read properties")
    }
    return properties
}

val currentLocalProperties = getLocalProperties()

val tagName = "1.0.1"
val tagCode = 101

android {
    namespace = "com.kieronquinn.app.pcs"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.kieronquinn.app.pcs"
        minSdk = 34
        targetSdk = 36
        versionCode = tagCode
        versionName = tagName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "TAG_NAME", "\"${tagName}\"")
    }

    signingConfigs {
        create("release") {
            if (currentLocalProperties != null) {
                storeFile = file(currentLocalProperties.getProperty("storeFile"))
                storePassword = currentLocalProperties.getProperty("storePassword")
                keyAlias = currentLocalProperties.getProperty("keyAlias")
                keyPassword = currentLocalProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
        release {
            //Minify is enabled but obfuscation is disabled
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
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
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
            freeCompilerArgs = listOf("-XXLanguage:+PropertyParamAnnotationDefaultTargetMode")
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
        aidl = true
    }
}

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        // x86 + x86_64 are not supported by Private Compute Services so no point including them
        variant.packaging.jniLibs.excludes.add("/lib/x86/*.so")
        variant.packaging.jniLibs.excludes.add("/lib/x86_64/*.so")
    }
}

sekret {
    properties {
        enabled.set(true)
        packageName.set("com.kieronquinn.app.pcs.sekret")
        encryptionKey.set(currentLocalProperties!!.getProperty("keyHash"))
    }
}

val grpcVersion = "1.74.0"
val protobufVersion = "4.32.0"
protobuf {
    protoc {
        // protoc compiler
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }
    plugins {
        // gRPC Java protoc plugin
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            // Generate Java (lite) for messages
            task.builtins {
                create("java") {
                    option("lite")
                }
            }
            // Generate Java (lite) for gRPC services
            task.plugins {
                id("grpc") {
                    option("lite")
                }
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    compileOnly(libs.xposed)
    implementation(libs.okhttp)
    implementation(libs.grpc.binder)
    implementation(libs.grpc.android)
    implementation(libs.grpc.stub)
    implementation(libs.grpc.okhttp)
    implementation(libs.grpc.protobuf.lite)
    implementation(libs.protobuf.java)
    implementation(libs.work)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.runtime.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.compose.preferences)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)

    implementation(libs.libsu.core)
    implementation(libs.dexkit)
    implementation(libs.tink)
    implementation(libs.annotatedText)
    implementation(libs.cardShape)
    implementation(libs.material.motion.compose.core)
    implementation(libs.accompanist.permissions)

    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)

    implementation(libs.aboutlibraries.core)
    implementation(libs.aboutlibraries.compose.m3)
    implementation(libs.markwon)
    implementation(libs.markwon.tables)

}
