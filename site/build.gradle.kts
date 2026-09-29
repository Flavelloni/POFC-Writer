import com.varabyte.kobweb.gradle.application.util.configAsKobwebApplication

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.kobweb.application)
}

group = "com.pofc"
version = "1.0-SNAPSHOT"

kobweb {
    app {
        index {
            description.set("Pineapple Open Face Chinese Poker scorekeeper")
        }
    }
}

kotlin {
    configAsKobwebApplication("pofc")

    sourceSets {
        jsMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.html.core)
            implementation(libs.kobweb.core)
            implementation(libs.kobweb.silk)
            implementation(libs.silk.icons.fa)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
