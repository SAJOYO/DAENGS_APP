pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // 카카오 SDK 는 **메이븐 센트럴에 없다.** 카카오가 자기 넥서스로만 배포한다
        // (`com.kakao.sdk` 를 센트럴에서 찾으면 404 다). 그래서 저장소를 하나 더 연다.
        //
        // `content { includeGroup }` 으로 **이 그룹만** 여기서 찾게 막아 둔다. 안 그러면
        // 다른 의존성도 매번 이 서버에 먼저 물어보게 되어, 카카오 서버가 느리거나
        // 죽으면 우리 빌드가 같이 느려진다.
        maven("https://devrepo.kakao.com/nexus/content/groups/public") {
            content { includeGroup("com.kakao.sdk") }
        }
    }
}

rootProject.name = "Daengs"
include(":app")
