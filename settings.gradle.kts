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
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // PyTorch AWS S3 repository for custom AAR builds if needed:
        // maven { url = uri("https://ossci-android.s3.amazonaws.com/executorch/release/") }
    }
}

rootProject.name = "executorchDemo"
include(":app")
