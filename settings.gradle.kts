pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Pleos Connect SDK 저장소(공식 문서 'Importing Vehicle SDK' 기준) — 접속 확인 필요
        maven { url = uri("https://nexus-playground.pleos.ai/repository/maven-releases/") }
    }
}
rootProject.name = "Chiilkkabwa"
include(":app")
