import org.gradle.kotlin.dsl.implementation

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}


android {
    namespace = "com.longtoast.bilbil"
    compileSdk = 36

    buildFeatures {
        viewBinding=true
        buildConfig = true
    }


    defaultConfig {
        applicationId = "com.longtoast.bilbil"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters.add("arm64-v8a")
            abiFilters.add("armeabi-v7a")
            abiFilters.add("x86")
            abiFilters.add("x86_64")
        }

    }



    buildTypes {
        release {
            isMinifyEnabled = true
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
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)


    implementation("com.kakao.sdk:v2-user:2.21.0")
    // Retrofit2 (HTTP 클라이언트)dd
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    //kakao map
    implementation("com.kakao.maps.open:android:2.12.17")

    // 💡 추가: 원형 프로필 이미지 뷰 (CircleImageView) 라이브러리 추가
    implementation("de.hdodenhof:circleimageview:3.1.0")

}