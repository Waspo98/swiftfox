import java.io.File
import java.io.StringReader
import java.util.Properties
import org.gradle.api.GradleException

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Configuration-cache safe branch detection via tracked .git/HEAD (supports git worktrees)
val gitHeadFile = run {
    val dotGit = rootProject.file(".git")
    if (dotGit.isFile) {
        val gitDirLine = dotGit.readLines().firstOrNull()?.trim().orEmpty()
        val relGitDir = gitDirLine.removePrefix("gitdir:").trim()
        val resolvedDir = File(relGitDir).let { if (it.isAbsolute) it else File(rootProject.projectDir, relGitDir) }
        File(resolvedDir, "HEAD")
    } else {
        File(dotGit, "HEAD")
    }
}
val gitBranch = providers.fileContents(layout.file(provider { gitHeadFile }))
    .asText
    .map { text ->
        val line = text.lines().firstOrNull()?.trim().orEmpty()
        if (line.startsWith("ref: refs/heads/")) line.removePrefix("ref: refs/heads/") else line
    }
    .getOrElse("")

val betaProp = providers.gradleProperty("betaBuild")
val isBeta = if (betaProp.isPresent) {
    betaProp.get().toBoolean()
} else {
    gitBranch.equals("beta", ignoreCase = true)
}

val localProps = Properties().apply {
    val text = providers.fileContents(rootProject.layout.projectDirectory.file("local.properties"))
        .asText
        .orNull
    if (text != null) load(StringReader(text))
}

fun releaseSecret(envKey: String, propKey: String): String? =
    providers.environmentVariable(envKey).orNull?.takeIf { it.isNotBlank() }
        ?: localProps.getProperty(propKey)?.takeIf { it.isNotBlank() }

val releaseKeystorePath = releaseSecret("KEYSTORE_PATH", "release.keystore.path")
val releaseKeystorePassword = releaseSecret("KEYSTORE_PASSWORD", "release.keystore.password")
val releaseKeyAlias = releaseSecret("KEY_ALIAS", "release.key.alias")
val releaseKeyPassword = releaseSecret("KEY_PASSWORD", "release.key.password")
val releaseKeystoreFile = releaseKeystorePath?.let { path ->
    val f = File(path)
    if (f.isAbsolute) f else rootProject.file(path)
}
val hasReleaseSigning = releaseKeystoreFile?.isFile == true &&
    !releaseKeystorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

val includeX86 = providers.gradleProperty("includeX86")
    .map { it.toBoolean() }
    .getOrElse(false)

val releasePackagingTaskRegex = Regex("""(?i)(^|:)(assemble|bundle|package|publish|sign).*Release""")
if (gradle.startParameter.taskNames.any { releasePackagingTaskRegex.containsMatchIn(it) } && !hasReleaseSigning) {
    throw GradleException(
        "Release signing is not configured. Provide KEYSTORE_PATH/KEYSTORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD " +
            "or release.keystore.* in local.properties before running release packaging tasks."
    )
}

android {
    namespace = "app.overbay.swiftfox"
    compileSdk = 36

    defaultConfig {
        applicationId = if (isBeta) "app.overbay.swiftfox.beta" else "app.overbay.swiftfox"
        minSdk = 31
        targetSdk = 35
        versionCode = 7
        versionName = if (isBeta) "0.2.2-beta" else "0.2.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += if (includeX86) listOf("arm64-v8a", "x86_64") else listOf("arm64-v8a")
        }
    }

    sourceSets {
        getByName("debug") {
            if (isBeta) {
                res.directories.add("src/beta/res")
            }
        }
        getByName("release") {
            if (isBeta) {
                res.directories.add("src/beta/res")
            }
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseKeystoreFile
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
        aidl = false
        shaders = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
}

kotlin {
    jvmToolchain(17)
}

ksp {
    arg("room.generateKotlin", "true")
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.androidx.webkit)
    implementation(libs.androidx.window)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.okhttp)
    implementation(libs.mozilla.service.firefox.accounts)
    implementation(libs.mozilla.browser.storage.sync)
    // Required at compile & runtime: PlacesBookmarksStorage/PlacesHistoryStorage implement AutocompleteProvider
    implementation(libs.mozilla.concept.toolbar)
    implementation(libs.mozilla.support.appservices)
    implementation(libs.mozilla.lib.fetch.okhttp)
    implementation("net.java.dev.jna:jna:${libs.versions.jna.get()}@aar")
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.zxing.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
}

abstract class CopyDebugApkTask : DefaultTask() {
    @get:InputFile
    abstract val sourceApk: RegularFileProperty

    @get:Input
    abstract val betaBuild: Property<Boolean>

    @get:Internal
    abstract val projectRootDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val src = sourceApk.get().asFile
        if (src.exists()) {
            val root = projectRootDir.get().asFile
            val betaApk = File(root, "SwiftfoxBeta-debug.apk")
            val standardApk = File(root, "Swiftfox-debug.apk")
            if (betaBuild.get()) {
                src.copyTo(betaApk, overwrite = true)
                if (standardApk.exists()) standardApk.delete()
                println("Copied ${src.name} -> SwiftfoxBeta-debug.apk")
            } else {
                src.copyTo(standardApk, overwrite = true)
                if (betaApk.exists()) betaApk.delete()
                println("Copied ${src.name} -> Swiftfox-debug.apk")
            }
        }
    }
}

tasks.register<CopyDebugApkTask>("copyDebugApkToRoot") {
    sourceApk.set(layout.buildDirectory.file("outputs/apk/debug/app-debug.apk"))
    betaBuild.set(isBeta)
    projectRootDir.set(layout.projectDirectory.dir(".."))
}

afterEvaluate {
    tasks.findByName("assembleDebug")?.finalizedBy("copyDebugApkToRoot")
}
