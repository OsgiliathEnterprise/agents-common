pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://repo.spring.io/release")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        mavenCentral()
        maven("https://repo.spring.io/release")

    }
}

rootProject.name = "agents-common"

val bridgeDir = file("../acp-langraph-langchain-bridge")
if (bridgeDir.exists()) {
    includeBuild(bridgeDir) {
        dependencySubstitution {
            substitute(module("net.osgiliath.ai:acp-langraph-langchain-bridge")).using(project(":"))
        }
    }
}

val agentSdk = file("../agent-sdk")
if (agentSdk.exists()) {
    includeBuild(agentSdk) {
        dependencySubstitution {
            substitute(module("net.osgiliath.ai:agent-sdk")).using(project(":"))
        }
    }
}
