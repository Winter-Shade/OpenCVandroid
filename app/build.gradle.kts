plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.wintershade.opencvandroid"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.wintershade.opencvandroid"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        externalNativeBuild {
            cmake {
                arguments += "-DOpenCV_DIR=/Users/srijanshukla/Downloads/OpenCV-android-sdk/sdk/native/jni"
                "-DANDROID_TOOLCHAIN=clang"
                "-DANDROID_STL=c++_shared"

                targets += "app"
                cppFlags += ""

            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(project(":OpenCV"))
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}