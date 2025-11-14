plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.longtoast.bilbil"
    compileSdk = 36

    buildFeatures {
        viewBinding = true
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
    // AndroidX & UI
    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.activity:activity-ktx:1.8.0")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.play.services.location)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation("androidx.recyclerview:recyclerview:1.3.0")

    // Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Kakao SDK & Kakao Map
    implementation("com.kakao.sdk:v2-user:2.21.0")
    implementation("com.kakao.maps.open:android:2.12.17")

    // CircleImageView (프로필 원형 이미지)
    implementation("de.hdodenhof:circleimageview:3.1.0")

    // --------------------------
    // Network: Retrofit + Gson + OkHttp + Logging
    // (중복 선언 정리)
    // --------------------------
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")

    // Coroutines (이미 선언되어 있었음 — 중복 적용주의)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Glide (이미지 로드) — kapt 없이 사용 (컴파일러는 생략)
    implementation("com.github.bumptech.glide:glide:4.15.1")
    // 만약 'kapt' 방식의 Glide annotation processor 사용을 원하면
    // plugins { id("org.jetbrains.kotlin.kapt") } 를 추가하고 아래를 사용하세요:
    // kapt "com.github.bumptech.glide:compiler:4.15.1"

    // 기타 유틸(선택)
    // implementation("com.squareup.moshi:moshi-kotlin:1.15.0") // 예시
}
