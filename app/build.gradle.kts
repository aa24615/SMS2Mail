plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.php127.sms2mail"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.php127.sms2mail"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }

    // JavaMail 等库会带入 META-INF/NOTICE.md、LICENSE.md 等文件，
    // 多个 JAR 同名冲突导致打包失败，这里排除掉（仅元数据，不影响运行）。
    packaging {
        resources {
            excludes += setOf(
                "META-INF/NOTICE.md",
                "META-INF/NOTICE",
                "META-INF/LICENSE.md",
                "META-INF/LICENSE",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE.txt"
            )
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    // 加密存储邮箱密码（避免明文落盘）
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    // 邮件发送（Android 版 JavaMail）
    implementation("com.sun.mail:android-mail:1.6.7")
    implementation("com.sun.mail:android-activation:1.6.7")
}
