import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

// يقرأ بيانات التوقيع من keystore.properties (ملف محلي غير مرفوع على Git).
// لو الملف مش موجود (زي بيئة الـ CI أو أول مرة)، الـ release build هيبني من غير توقيع
// بدل ما يفشل، عشان تقدر تكمل شغل عادي وتضيف الملف وقت الرفع الفعلي فقط.
val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")
val hasSigningConfig = keystorePropertiesFile.exists()
if (hasSigningConfig) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

android {
    namespace = "com.localexpense.tracker"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.localexpense.tracker"
        minSdk = 26
        // Google Play بيلزم كل التطبيقات الجديدة تستهدف Android 16 (API 36)
        // اعتبارًا من 31 أغسطس 2026. راجع:
        // https://developer.android.com/google/play/requirements/target-sdk
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // فلاج بيتحكم في إظهار/تفعيل مسار قراءة الـ SMS المباشرة داخل الكود.
        // بيتغيّر قيمته تلقائيًا حسب الـ flavor (play / direct) تحت.
        buildConfigField("boolean", "ENABLE_SMS_IMPORT", "true")
    }

    signingConfigs {
        if (hasSigningConfig) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    // يفصل التطبيق لنسختين من نفس الكود:
    // - play:   نسخة متجر جوجل بلاي. من غير RECEIVE_SMS/READ_SMS إطلاقًا، تعتمد
    //           فقط على قراءة إشعارات تطبيقات البنوك (Notification Listener) +
    //           الإدخال اليدوي. أكثر توافقًا مع سياسة الأذونات المقيدة في جوجل بلاي.
    // - direct: النسخة الكاملة (زي ما هي دلوقتي) بما فيها الالتقاط الفوري لرسائل
    //           الـ SMS، تُوزَّع كـ APK مباشر بره المتجر (GitHub مثلاً) لمين ما
    //           يفضّل الميزة دي بالذات.
    flavorDimensions += "distribution"
    productFlavors {
        create("play") {
            dimension = "distribution"
            buildConfigField("boolean", "ENABLE_SMS_IMPORT", "false")
        }
        create("direct") {
            dimension = "distribution"
            applicationIdSuffix = ".direct"
            buildConfigField("boolean", "ENABLE_SMS_IMPORT", "true")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
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
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // مخططات Room المولّدة (نسخة 6 وما بعدها) تُصدَّر هنا وتُقرأ في اختبار
    // الترقية (MigrationTestHelper) من أصول الـ androidTest.
    sourceSets["androidTest"].assets.srcDir("$projectDir/schemas")
}

// مكان تصدير مخططات Room (JSON) لكل نسخة — مطلوب للتحقق من الترقيات.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // تشفير قاعدة البيانات المحلية (بيانات مالية حساسة) - كله على الجهاز،
    // من غير أي اتصال بالإنترنت. راجع SecurePassphraseProvider.kt.
    // لازم تفضل الحزمة دي بالتحديد (sqlcipher-android) مش الحزمة القديمة
    // net.zetetic:android-database-sqlcipher: مكتبات الـ native في القديمة
    // مرصوصة على 4 KB (0x1000)، وجوجل بلاي بترفض أي تطبيق بيستهدف Android 15+
    // مش داعم صفحات 16 KB. الحزمة دي مرصوصة على 16 KB (0x4000).
    // باكيدجها net.zetetic.database.sqlcipher (مش net.sqlcipher) وواجهتها
    // مختلفة - راجع AppDatabase.kt و DatabaseEncryptionMigration.kt.
    implementation("net.zetetic:sqlcipher-android:4.6.1")
    implementation("androidx.sqlite:sqlite:2.4.0")

    implementation("androidx.datastore:datastore-preferences:1.1.1")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // اختبارات وحدة على الـ JVM (منطق الفلوس والتحليل) — من غير أي جهاز.
    testImplementation("junit:junit:4.13.2")

    // اختبار ترقية قاعدة البيانات (MigrationTestHelper) — بيتشغّل على جهاز/محاكي.
    androidTestImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
}
