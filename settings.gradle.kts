pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()

        // Kakao Map
        maven("https://devrepo.kakao.com/nexus/content/groups/public/")
        maven("https://devrepo.kakao.com/nexus/repository/kakaomap-releases/")

        // Naver Map
        maven("https://repository.map.naver.com/archive/maven")
    }
}

rootProject.name = "BilBil"
include(":app")
