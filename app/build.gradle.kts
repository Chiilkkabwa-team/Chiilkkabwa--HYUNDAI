// Kotlin DSL에서 `java.util.Properties`를 그대로 쓰면 `java`가 Gradle의 java 확장으로 잡혀 깨진다 → import로 받는다
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.chiilkkabwa.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.chiilkkabwa.app"
        minSdk = 28            // Pleos 요구(사이닝 키 로테이션·리니지) → API 28+
        targetSdk = 34         // Pleos Connect 에뮬레이터 v2.1.0 = API 34
        versionCode = 1
        versionName = "0.1.0"
    }

    flavorDimensions += "signals"
    productFlavors {
        // 모의 차량 + 디버그 패널: SDK·에뮬레이터 없이 전체 흐름 시연·테스트
        create("mock") { dimension = "signals"; buildConfigField("boolean", "USE_MOCK", "true"); applicationIdSuffix = ".mock" }
        // 실제 신호: AAOS Car API + Pleos Vehicle SDK (에뮬레이터·실차)
        create("vehicle") { dimension = "signals"; buildConfigField("boolean", "USE_MOCK", "false") }
    }

    // AAOS Car API(CarUxRestrictionsManager, CarPropertyManager)
    useLibrary("android.car")

    /**
     * release 서명.
     *
     * 기본값은 **디버그 키**다. 공모전 제출 APK는 설치만 되면 되고(스토어 배포가 아니다),
     * 서명 키스토어를 저장소에 넣으면 그게 더 큰 문제이기 때문이다.
     * 자기 키로 서명하려면 저장소 루트에 `keystore.properties`를 만들고(`.gitignore`에 이미 있다):
     *   storeFile=../my-release.jks
     *   storePassword=…
     *   keyAlias=…
     *   keyPassword=…
     * Pleos는 minSdk 28을 요구한다(사이닝 키 로테이션·리니지) → 실제 스토어 배포 때는 키 로테이션을 지원하는 v3 서명을 쓴다.
     */
    val keystoreProps = rootProject.file("keystore.properties")
    if (keystoreProps.exists()) {
        val p = Properties()
        keystoreProps.inputStream().use { stream -> p.load(stream) }
        signingConfigs.create("release") {
            storeFile = rootProject.file(p.getProperty("storeFile"))
            storePassword = p.getProperty("storePassword")
            keyAlias = p.getProperty("keyAlias")
            keyPassword = p.getProperty("keyPassword")
        }
    }

    buildTypes {
        debug {
            // A01 실수신 검증 전 기본 차단. opt-in도 에뮬레이터에서만 사용한다.
            buildConfigField("boolean", "PLEOS_SIGNALS_ENABLED", (providers.gradleProperty("enablePleosSignals").orNull == "true").toString())
        }
        release {
            buildConfigField("boolean", "PLEOS_SIGNALS_ENABLED", "false")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
}

dependencies {
    // ── Pleos Connect SDK (버전은 문서 기준. Sync 실패 시 docs/03의 저장소·버전 재확인)
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    // 아래는 My Project에서 '사용 가능' 확인 후 주석 해제
    // implementation("ai.pleos.playground:TextToSpeech:3.3.1")

    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
