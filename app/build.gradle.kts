import com.android.build.api.artifact.SingleArtifact
import org.gradle.internal.extensions.stdlib.capitalized

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.maksz42.periscope"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    enableKotlin = false

    defaultConfig {
        applicationId = "com.maksz42.periscope"
        minSdk = 8
        // TODO api 35
        // forces edge-to-edge
        targetSdk = 34
        versionCode = 23
        versionName = "0.23"
        resValue("string", "app_version_name", versionName!!)

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
        resValues = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    compileOnly(libs.annotation.jvm)
    implementation(libs.conscrypt.android)
//    implementation(libs.appcompat)
    testImplementation(libs.junit)
//    androidTestImplementation(libs.ext.junit)
//    androidTestImplementation(libs.espresso.core)
}

androidComponents {
    val keystoreFile = providers.gradleProperty("PeriscopeOtaKeystoreFile")
    val keystorePassword = providers.gradleProperty("PeriscopeOtaKeystorePassword")

    val sdkDir = sdkComponents.sdkDirectory.get()
    val buildToolsVersion = android.buildToolsVersion
    val zipalign = "$sdkDir/build-tools/$buildToolsVersion/zipalign"
    val apksigner = "$sdkDir/build-tools/$buildToolsVersion/apksigner"

    onVariants(selector().withBuildType("release")) { variant ->
        val variantName = variant.name.capitalized()

        val intermediatesDir = layout.buildDirectory.dir("outputs/intermediatesDir/${variant.name}")
        val otaDir = layout.buildDirectory.dir("outputs/ota/${variant.name}")
        val apkDir = variant.artifacts.get(SingleArtifact.APK)

        val releaseApk = apkDir.map { directory ->
            val builtArtifactsLoader = variant.artifacts.getBuiltArtifactsLoader()
            val builtArtifacts = builtArtifactsLoader.load(directory)
                    ?: error(
                        "Could not load APK metadata from " + directory.asFile
                    )

            val builtArtifact = builtArtifacts.elements.singleOrNull()
                    ?: error(
                        "Expected exactly one APK for ${variant.name}, found ${builtArtifacts.elements.size}"
                    )

            builtArtifact.outputFile
        }
        val alignedApk = intermediatesDir.map { it.file("app-zipaligned.apk") }
        val signedApk = intermediatesDir.map { it.file("app-signed.apk") }
        val pigzedApk = intermediatesDir.map { it.file("app-signed.apk.gz") }

        val zipalignTask = tasks.register<Exec>("zipalign${variantName}Ota") {
            inputs.file(releaseApk)
            outputs.file(alignedApk)

            executable(zipalign)
            args("-z", "-f", "4")

            argumentProviders.add(
                CommandLineArgumentProvider {
                    listOf(
                        releaseApk.get(),
                        alignedApk.get().asFile.absolutePath
                    )
                }
            )
        }

        val apksignerTask = tasks.register<Exec>("sign${variantName}Ota") {
            dependsOn(zipalignTask)

            inputs.file(alignedApk)
            inputs.file(keystoreFile)
            outputs.file(signedApk)

            val keystorePasswordEnvKey = "APKSIGNER_KS_PASS"

            doFirst {
                environment(keystorePasswordEnvKey, keystorePassword.get())
            }

            executable(apksigner)
            args(
                "sign",
                "--v1-signing-enabled", "true",
                "--v2-signing-enabled", "true",
                "--v3-signing-enabled", "false",
                "--v4-signing-enabled", "false",
                "--debuggable-apk-permitted", "false",
                "--ks-pass", "env:$keystorePasswordEnvKey",
            )

            argumentProviders.add(
                CommandLineArgumentProvider {
                    listOf(
                        "--ks", keystoreFile.get(),
                        "--in", alignedApk.get().asFile.absolutePath,
                        "--out", signedApk.get().asFile.absolutePath
                    )
                }
            )
        }

        val pigzTask = tasks.register<Exec>("pigz${variantName}Ota") {
            dependsOn(apksignerTask)

            inputs.file(signedApk)
            outputs.file(pigzedApk)

            executable("pigz")
            args("-11", "-k", "-n", "-f")

            argumentProviders.add(
                CommandLineArgumentProvider {
                    listOf(
                        signedApk.get().asFile.absolutePath
                    )
                }
            )
        }

        tasks.register<Sync>("assemble${variantName}Ota") {
            group = "Build"
            description = "Builds OTA artifacts"

            dependsOn(pigzTask)

            val variantOutput = variant.outputs.single()
            val versionCode = variantOutput.versionCode
            val versionName = variantOutput.versionName

            inputs.property("versionCode", versionCode)
            inputs.property("versionName", versionName)

            from(signedApk) {
                rename { "periscope.apk" }
            }

            from(pigzedApk) {
                rename { "periscope.apk.gz" }
            }

            into(otaDir)

            doLast {
                val outDir = otaDir.get()
                outDir.file("version_code").asFile.writeText(versionCode.get().toString())
                outDir.file("version_name").asFile.writeText(versionName.get())
                println(outDir.asFile.absolutePath)
            }
        }
    }
}