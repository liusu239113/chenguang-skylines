plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.dshx.game.she"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dshx.game.she"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "1.0.5"
    }

    signingConfigs {
        create("release") {
            storeFile = file("../keystore/chenguang.p12")
            storePassword = "chenguang123"
            keyAlias = "chenguang"
            keyPassword = "chenguang123"
            storeType = "PKCS12"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            pickFirsts += setOf("META-INF/NOTICE", "META-INF/LICENSE", "META-INF/DEPENDENCIES")
        }
        jniLibs {
            pickFirsts += setOf("**/*.so")
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.cardview:cardview:1.0.0")

    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("com.squareup.retrofit2:adapter-rxjava2:2.2.0")
    implementation("io.reactivex.rxjava2:rxjava:2.2.21")

    implementation(files("libs/tosin-ad-Y260817.aar"))
    implementation(files("libs/tosin-adx-2.9.65.aar"))
    implementation(files("libs/oaid_sdk_1.0.25.aar"))
    implementation(files("libs/tosin-csj-adapter-7.6.1.1.aar"))
    implementation(files("libs/tosin-gdt-adapter-4.690.1560.aar"))
    implementation(files("libs/tosin-ks-adapter-5.1.20.1.aar"))
    implementation(files("libs/tosin-baidu-adapter-9.450.aar"))
    implementation(files("libs/sigmob/tosin-sigmob_common-adapter-1.9.4.aar"))
    implementation(files("libs/sigmob/tosin-sigmob_windsdk-adapter-4.25.11.aar"))
    implementation(files("libs/topon/tosin-anythink_banner-adapter.aar"))
    implementation(files("libs/topon/tosin-anythink_china_core.aar"))
    implementation(files("libs/topon/tosin-anythink_core-adapter.aar"))
    implementation(files("libs/topon/tosin-anythink_interstitial-adapter.aar"))
    implementation(files("libs/topon/tosin-anythink_native-adapter.aar"))
    implementation(files("libs/topon/tosin-anythink_rewardvideo-adapter.aar"))
    implementation(files("libs/topon/tosin-anythink_splash-adapter.aar"))
    implementation(files("libs/topon/tosin-anythink_adx_sdk_kuying_necessary-adapter-6.5.48.aar"))
    implementation(files("libs/topon/tosin-anythink_network_adx_kuying_sdk_necessary-adapter.aar"))
    implementation(files("libs/yout/tosin-adalliance-adapter-4.7.7.aar"))
    implementation(files("libs/taptap/tosin-taptap-adapter-4.2.4.8.aar"))
    implementation(files("libs/adgain/tosin-adgainsdk-adapter-4.2.7.2.aar"))
    implementation(files("libs/adgain/tosin-adgainbeizi-adapter-4.2.5.4.aar"))
    implementation(files("libs/adgain/tosin-adgaingromore-adapter-4.2.7.aar"))
    implementation(files("libs/adgain/tosin-adgainjiguang-adapter-4.2.2.1.aar"))
    implementation(files("libs/adgain/tosin-adgaintaku-adapter-4.2.7.aar"))
    implementation(files("libs/adgain/tosin-adgaintobid-adapter-4.2.7.aar"))
    implementation(files("libs/adgain/tosin-admate-adapter-4.2.7.aar"))
    implementation(files("libs/adgain/tosin-mediatom-adapter-4.2.5.2.aar"))
    implementation(files("libs/adview/tosin-adview-adapter-5.0.5.aar"))
    implementation(files("libs/beizi/tosin-beizi-adapter-5.3.0.3.aar"))
    implementation(files("libs/dm/tosin-domob-adapter-3.8.2.aar"))
    implementation(files("libs/funlink/tosin-funlink-adapter-2.9.0_77390768.aar"))
    implementation(files("libs/funlink/tosin-funlink_gromore-adapter-2.9.0_77328722.aar"))
    implementation(files("libs/funlink/tosin-funlink_taku-adapter-2.9.0_77328722.aar"))
    implementation(files("libs/funlink/tosin-funlink_tobid-adapter-2.9.0_77328722.aar"))
    implementation(files("libs/hx/tosin-hx-sdk-1.6.17.aar"))
    implementation(files("libs/hx/tosin-hx-gromore-adapter.aar"))
    implementation(files("libs/hx/tosin-hx-mediatom-adapter.aar"))
    implementation(files("libs/hx/tosin-hx-taku-adapter.aar"))
    implementation(files("libs/hx/tosin-hx-tobid-adapter.aar"))
    implementation(files("libs/jiatou/tosin-advista-adapter-1.9.2.aar"))
    implementation(files("libs/jutui/tosin-jutui-adapter-4.2.3.1.aar"))
    implementation(files("libs/maimeng/tosin-wm-adapter-7.9.19.25.aar"))
    implementation(files("libs/ms/tosin-ms-adapter-3.0.4.1.aar"))
    implementation(files("libs/tianxuan/tosin-UBiX-adapter-2.10.1.11.aar"))
    implementation(files("libs/zhongchen/tosin-starsads-adapter-1.3.04.aar"))

    implementation("com.taptap.sdk:tap-core:4.10.3")
    implementation("com.taptap.sdk:tap-login:4.10.3")
    implementation("com.taptap.sdk:tap-compliance:4.10.3")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
