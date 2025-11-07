
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

//d
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

//이제 보이시나요



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


    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)


    // ❌ [제거] StompProtocolAndroid 및 모든 RxJava 의존성
    // implementation("com.github.NaikSoftware:StompProtocolAndroid:1.6.6")
    // implementation("io.reactivex.rxjava3:rxandroid:3.0.0")
    // implementation("io.reactivex.rxjava3:rxjava:3.1.6")
    // implementation("io.reactivex.rxjava2:rxandroid:2.1.1")
    // implementation("io.reactivex.rxjava2:rxjava:2.2.21")

    // ✅ [추가] Krossbow STOMP 클라이언트
    implementation("org.hildan.krossbow:krossbow-stomp-websocket:3.1.0")

    // ✅ [추가] Kotlin Coroutines (비동기 처리)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

    // Gson과 OkHttp는 유지
}