plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.androidx.room) apply false
    alias(libs.plugins.composeHotReload) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlinAndroid) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
}

val composeSkikoVersion = libs.versions.skiko.get()

subprojects {
    dependencies {
        components {
            all {
                if (id.group == "io.coil-kt.coil3") {
                    allVariants {
                        withDependencies {
                            // 仅对 Coil 的旧 Skiko 声明做对齐，避免影响其他 Compose 依赖。
                            val removedSkiko = removeAll {
                                it.group == "org.jetbrains.skiko" && it.name == "skiko"
                            }
                            if (removedSkiko) {
                                add("org.jetbrains.skiko:skiko:$composeSkikoVersion")
                            }
                        }
                    }
                }
            }
        }
    }
}
