plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// local.properties 에서 설정값을 읽는다. **커밋되는 파일이 아니다** — sdk.dir 이
// 들어 있는 그 파일이고 .gitignore 에 있다. 키를 저장소에 넣지 않으려면 여기가 맞다.
//
// providers.fileContents 를 쓰는 이유: gradle.properties 에 configuration-cache 가
// 켜져 있어서, 파일을 직접 읽으면 설정 캐시가 그 읽기를 못 따라간다.
fun localSetting(key: String): String =
    providers.fileContents(rootProject.layout.projectDirectory.file("local.properties"))
        .asText.orNull
        ?.lineSequence()
        ?.map { it.trim() }
        ?.firstOrNull { it.startsWith("$key=") }
        ?.substringAfter("=")
        ?.trim()
        ?: ""

val kakaoNativeAppKey = localSetting("daengs.kakaoNativeAppKey")
val apiBaseUrl = localSetting("daengs.apiBaseUrl")

// 피부 스크리닝 서버. 우리 서버(apiBaseUrl)와 **다른 주소**다 — 모델이 저쪽
// 저장소(gayeoniee/deeplearning_test)에서 따로 돌고, 아직 띄워 두지도 않았다.
// 비어 있으면 채팅의 진단 버튼이 스스로 그렇게 말한다.
val screenUrl = localSetting("daengs.screenUrl")

// 네이버 지도 NCP 키. 없어도 앱은 켜진다 — 지도 타일만 인증 실패로 비고,
// 나머지 화면은 그대로 돈다 (카카오 키와 같은 철학).
val naverMapClientId = localSetting("daengs.naverMapClientId")

android {
    namespace = "com.daengs.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.daengs.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 값이 없으면 빈 문자열이다. 그 상태로도 앱은 켜지고 "둘러보기" 로 방까지
        // 들어가진다 — 랜딩 화면이 설정이 없다고 알려 준다.
        buildConfigField("String", "KAKAO_NATIVE_APP_KEY", "\"$kakaoNativeAppKey\"")
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
        buildConfigField("String", "SCREEN_BASE_URL", "\"$screenUrl\"")
        buildConfigField("String", "NAVER_MAP_NCP_KEY_ID", "\"$naverMapClientId\"")

        // 카카오 리다이렉트 스킴. 매니페스트가 이 자리를 비워 두고 여기서 꽂는다.
        manifestPlaceholders["kakaoScheme"] = "kakao$kakaoNativeAppKey"
    }

    // 디버그 서명 키를 저장소에 넣어 공유한다.
    // PC 마다 다른 ~/.android/debug.keystore 로 서명되면 폰에 이미 깔린 앱을
    // 덮어쓰지 못하고 INSTALL_FAILED_UPDATE_INCOMPATIBLE 이 난다.
    // (디버그 전용 키라 공개해도 안전하다. 릴리스 키는 절대 커밋하지 않는다)
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

// Room 스키마는 코드와 함께 리뷰한다. 테이블 변경이 JSON diff로 남아야 다음 버전에서
// 알려진 이전 구조를 대상으로 마이그레이션을 작성할 수 있다.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.security.crypto)
    implementation(libs.kakao.user)
    implementation(libs.naver.map.sdk)
    implementation(libs.play.services.location)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    // 안드로이드의 org.json 은 **프레임워크 안에만** 있고, 단위 테스트가 도는 JVM
    // 에서는 모든 메서드가 "not mocked" 예외를 던지는 껍데기다. 진짜 구현을 테스트
    // 클래스패스에 얹어 그 껍데기를 가린다. 앱 APK 에는 안 들어간다.
    testImplementation(libs.org.json)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
