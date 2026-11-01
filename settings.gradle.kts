pluginManagement {
    repositories {
        // 👇 阿里云镜像（加到最前面）
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin/") }
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

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 👇 阿里云镜像（加到最前面）
        // maven { url = uri("https://maven.aliyun.com/repository/public/") }
        // maven { url = uri("https://maven.aliyun.com/repository/google/") }
        google()
        mavenCentral()
    }
}

rootProject.name = "MessageKeeper"
include(":app")