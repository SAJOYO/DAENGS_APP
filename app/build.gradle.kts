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

// 보행 분석 서버. 스크리닝과 **같은 모양**이다 — nginx 가 우리 서버와 같은 호스트에서
// /gait 접두사로 별도 컨테이너에 넘긴다. 그래서 daengs_backend 의 openapi.json 에는
// 안 나오고, 계약은 저쪽 저장소의 backend/src/daengs_gait/API.md 에 있다.
// 비어 있으면 보행 줄이 스스로 그렇게 말한다.
val gaitUrl = localSetting("daengs.gaitUrl")

/**
 * 출시 빌드가 볼 주소.
 *
 * **개발 서버는 그대로 두고 릴리즈만 가른다.** 개발 서버는 `http://` 인데 평문 HTTP
 * 허용이 디버그 소스셋에만 있어서, 릴리즈 빌드는 개발 서버로 요청이 소켓 단계에서
 * 죽는다. 그렇다고 개발 서버를 https 로 옮기면 매번 주소를 바꿔 끼워야 한다.
 *
 * GCP 의 `daengapi` 한 호스트가 백엔드·진단(`/screen`)·보행(`/gait`)·장소를 전부
 * 받으므로, 셋 다 같은 호스트를 가리키게 된다.
 *
 * **비어 있으면 개발 값으로 떨어진다** — 새 키를 안 넣은 사람의 빌드가 지금과 똑같이
 * 동작해야 한다. 다만 그때는 아래에서 경고를 낸다.
 */
val apiBaseUrlRelease = localSetting("daengs.apiBaseUrlRelease")
val screenUrlRelease = localSetting("daengs.screenUrlRelease")
val gaitUrlRelease = localSetting("daengs.gaitUrlRelease")

/**
 * 릴리즈 값이 없으면 개발 값으로 떨어지되 **조용히 넘어가지 않는다.**
 *
 * 조용히 떨어지면 개발 서버(`http`)를 보는 릴리즈 빌드가 나오는데, 그건 켜지기는
 * 하고 통신만 죽어서 "왜 로그인이 안 되지" 로 한참 헤맨다.
 */
fun releaseUrl(name: String, release: String, fallback: String): String =
    if (release.isNotBlank()) {
        release
    } else {
        logger.warn(
            "⚠ daengs.${name}Release 가 비어 있어 릴리즈 빌드가 개발 주소를 씁니다. " +
                "개발 서버는 http 라 릴리즈에서는 통신이 막힙니다.",
        )
        fallback
    }

// 네이버 지도 NCP 키. 없어도 앱은 켜진다 — 지도 타일만 인증 실패로 비고,
// 나머지 화면은 그대로 돈다 (카카오 키와 같은 철학).
val naverMapClientId = localSetting("daengs.naverMapClientId")

// 콘솔 Style Editor 에서 만든 지도 스타일(My Style ID). 지도를 앱 팔레트로 칠한다.
// **없으면 기본 네이버 지도로 뜬다** — 앱은 정상 동작하고, 스타일만 안 입는다.
val naverMapStyleId = localSetting("daengs.naverMapStyleId")

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

        // 특정 CPU 용 라이브러리만 담아 APK 를 줄인다. **기본은 꺼짐이라 평소 빌드는
        // 지금과 똑같다** — `-PslimAbi=arm64-v8a` 를 준 빌드만 걸러진다.
        //
        // 왜 필요한가: 네이버 지도의 `libnavermap.so` 가 CPU 4종류만큼 들어 있어
        // `lib/` 만 91MB 다(arm64 23.7 · x86_64 25.0 · x86 24.3 · armeabi-v7a 18.0).
        // 링크로 받아 폰에 까는 데모용으로는 arm64 하나면 되고, 그러면 130MB 가
        // 63MB 가 된다. 2015년 이후 안드로이드 폰은 전부 arm64 다.
        //
        // ⚠️ **인텔 PC 에뮬레이터에서는 안 돈다.** 평소 개발 빌드에 이걸 걸면 안 되는
        // 이유고, 그래서 기본을 꺼 뒀다. 쉼표로 여럿도 된다 (`arm64-v8a,x86_64`).
        val slimAbi = providers.gradleProperty("slimAbi").orNull
        if (!slimAbi.isNullOrBlank()) {
            ndk { abiFilters += slimAbi.split(",").map { it.trim() }.filter { it.isNotEmpty() } }
        }

        // 값이 없으면 빈 문자열이다. 그 상태로도 앱은 켜지고 "둘러보기" 로 방까지
        // 들어가진다 — 랜딩 화면이 설정이 없다고 알려 준다.
        buildConfigField("String", "KAKAO_NATIVE_APP_KEY", "\"$kakaoNativeAppKey\"")
        buildConfigField("String", "NAVER_MAP_NCP_KEY_ID", "\"$naverMapClientId\"")
        buildConfigField("String", "NAVER_MAP_STYLE_ID", "\"$naverMapStyleId\"")
        // 서버 주소 셋은 여기가 아니라 **buildTypes 에서** 꽂는다. 개발과 출시가
        // 다른 서버를 보기 때문이다 (위 apiBaseUrlRelease 주석).

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
        debug {
            // 개발 서버. `http://` 라서 디버그 소스셋의 usesCleartextTraffic 이 필요하다
            // (`app/src/debug/AndroidManifest.xml`).
            buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
            buildConfigField("String", "SCREEN_BASE_URL", "\"$screenUrl\"")
            buildConfigField("String", "GAIT_BASE_URL", "\"$gaitUrl\"")
        }
        release {
            optimization {
                enable = false
            }
            // 출시 서버. https 라 평문 예외가 필요 없다.
            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"${releaseUrl("apiBaseUrl", apiBaseUrlRelease, apiBaseUrl)}\"",
            )
            buildConfigField(
                "String",
                "SCREEN_BASE_URL",
                "\"${releaseUrl("screenUrl", screenUrlRelease, screenUrl)}\"",
            )
            buildConfigField(
                "String",
                "GAIT_BASE_URL",
                "\"${releaseUrl("gaitUrl", gaitUrlRelease, gaitUrl)}\"",
            )
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
    // 보행 영상 재생. **시스템 플레이어로 던지지 않는다** — 이 기능의 요점이
    // "같은 아이의 두 시점을 나란히 본다" 라, 앱 밖으로 나가면 한 번에 한 편씩만
    // 보게 되어 비교가 안 된다.
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    // 앱 안 카메라. **가이드를 찍는 동안 보여 주려고** 넣는다 — 시스템 카메라로
    // 던지면 그 위에 아무것도 못 얹는다. 피부 사진은 병변에 네모를 맞춰야 하고
    // 보행 영상은 뒤에서 전신이 들어와야 하는데, 둘 다 찍고 나서 알면 늦다.
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.video)
    implementation(libs.androidx.camera.view)
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
