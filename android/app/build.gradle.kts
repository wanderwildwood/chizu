import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import java.util.Properties
import java.util.regex.Pattern

buildscript {
    repositories {
        google()
        mavenCentral()
    }

    dependencies {
        classpath(libs.android.tools)
    }
}

plugins {
    id("com.android.application")
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.aboutlibraries.android)
    alias(libs.plugins.dependencyUpdates)
}

tasks.named<DependencyUpdatesTask>("dependencyUpdates") {
  revision = "release"
  rejectPreReleases = true
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

val appName = "Atlas"
val appId = "com.wanderwildwood.chizu"
// Our own key, kept in the repo's gitignored signing/ and never committed. Without it every
// build comes out unsigned (debug builds fall back to the SDK's own debug key): there is no
// committed key and no fallback that could sign a release with one.
val signingPropertiesFile = rootProject.file("../signing/signing.properties")
val signingProperties = Properties().apply {
    if (signingPropertiesFile.isFile) signingPropertiesFile.inputStream().use(::load)
}
val hasRealKey = signingPropertiesFile.isFile

android {
    namespace = "app.organicmaps"

    // required for stripWebReleaseDebugSymbols
    ndkVersion = "28.2.13676358"

    dependenciesInfo {
        // Disables dependency metadata when building APKs for IzzyOnDroid, F-Droid, and Codeberg releases.
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles (for Google Play)
        includeInBundle = false
    }

    buildFeatures {
        dataBinding = true
        compose = true // the shared About and Licenses pages
        buildConfig = true
    }

    // Users are complaining that the app should be re-downloaded from the Play Store after changing the language.
    bundle {
        language {
            enableSplit = false
        }
    }

    compileSdk = providers.gradleProperty("propCompileSdkVersion").get().toInt()

    defaultConfig {
        versionCode = rootProject.ext.get("versionCode") as Int
        versionName = rootProject.ext.get("versionName") as String
        applicationId = appId
        minSdk = providers.gradleProperty("propMinSdkVersion").get().toInt()
        targetSdk = providers.gradleProperty("propTargetSdkVersion").get().toInt()
        base.archivesName = "${appName.replace(" ", "")}-${defaultConfig.versionCode!!}"
        ndk.debugSymbolLevel = "full"
        buildConfigField("String", "REVIEW_URL", "\"\"")
        buildConfigField("String", "SUPPORT_MAIL", "\"email@wanderthe.dev\"")
        println("Version: ${versionName!!}")
        println("VersionCode: ${versionCode!!}")
    }

    flavorDimensions += "default"

    // One build (Atlas): CoMaps' Google Play, Huawei and plain-APK builds and their store
    // publishing are gone. The F-Droid build is the one kept, under our own application id.
    productFlavors {
        create("fdroid") {
            dimension = "default"
            versionName = android.defaultConfig.versionName!!
        }
    }

    splits.abi {
        isEnable = project.hasProperty("splitApk").also { println("Create separate apks: $it") }
        reset()
        include("x86", "armeabi-v7a", "arm64-v8a", "x86_64")
        isUniversalApk = true
    }

    // Only bundle wrap.sh when arm64-v8a is actually compiled.
    val buildsArm64 = project.hasProperty("arm64") || project.hasProperty("arm64-v8a") ||
        !(project.hasProperty("arm32") || project.hasProperty("armeabi-v7a") ||
            project.hasProperty("x86") || project.hasProperty("x86_64") || project.hasProperty("x64"))
    if (buildsArm64) {
        sourceSets.getByName("debug") {
            resources.srcDir("src/debugArm64/resources")
        }
    }

    lint {
        disable += "MissingTranslation"
        // https://github.com/organicmaps/organicmaps/issues/3551
        disable += listOf("MissingQuantity", "UnusedQuantity")
        // https://github.com/organicmaps/organicmaps/issues/1077
        disable += "CustomSplashScreen"
        // https://github.com/organicmaps/organicmaps/issues/3610
        disable += "InsecureBaseConfiguration"
        abortOnError = true
    }

    gradle.projectsEvaluated {
        android.applicationVariants.configureEach {
            val taskName = name.replaceFirstChar(transform = Char::uppercase)
            tasks.register<Exec>(name = "run$taskName") {
                dependsOn("install$taskName")
                commandLine(
                    android.adbExecutable,
                    "shell",
                    "am",
                    "start",
                    "-n",
                    "$applicationId/app.organicmaps.DownloadResourcesActivity",
                    "-a",
                    "android.intent.action.MAIN",
                    "-c",
                    "android.intent.category.LAUNCHER"
                )
            }
        }
    }

    signingConfigs {
        if (hasRealKey) {
            create("real") {
                storeFile = rootProject.file("../signing/signing.keystore")
                storePassword = signingProperties.getProperty("STORE_PASSWORD")
                keyAlias = signingProperties.getProperty("KEY_ALIAS")
                keyPassword = signingProperties.getProperty("KEY_PASSWORD")
            }
        }
    }

    buildTypes {

        debug {
            applicationIdSuffix = ".debug" // Allows installing debug and release builds together.
            versionNameSuffix = "-debug"
            if (hasRealKey) signingConfig = signingConfigs["real"]
            resValue("string", "app_name", "$appName Debug")
        }
        release {
            // Unsigned without signing/, never signed with a debug key.
            signingConfig = if (hasRealKey) signingConfigs["real"] else null

            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            resValue("string", "app_name", appName)
        }

        create("beta") {
            applicationIdSuffix = ".test"
            versionNameSuffix = "-test"
            signingConfig = if (hasRealKey) signingConfigs["real"] else null
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            matchingFallbacks += "release" // Use dependencies of "release" build type.
            resValue("string", "app_name", "$appName Test")
        }
    }

    // We don't compress these extensions in assets/ because our random FileReader can't read zip-compressed files from apk.
    // TODO: Load all minor files via separate call to ReadAsString which can correctly handle compressed files in zip containers.
    androidResources {
        ignoreAssetsPattern = "!.svn:!.git:!.DS_Store:!*.scc:.*:<dir>_*:!CVS:!thumbs.db:!picasa.ini:!*~"
        noCompress += listOf("txt", "bin", "html", "png", "json", "mwm", "ttf", "sdf", "ui", "config", "csv", "spv", "obj")
        // Some languages not supported by Android require to be specified here to be applied
        localeFilters += listOf(
            "en",
            "af",
            "ar",
            "az",
            "be",
            "bg",
            "bn",
            "ca",
            "cs",
            "da",
            "de",
            "el",
            "en-rAU",
            "en-rGB",
            "es",
            "es-rMX",
            "et",
            "eu",
            "fa",
            "fi",
            "fr",
            "fr-rCA",
            "gl",
            "gsw",
            "he",
            "hi",
            "hu",
            "id",
            "in",
            "is",
            "it",
            "iw",
            "ja",
            "kw",
            "ko",
            "lt",
            "lv",
            "mr",
            "mt",
            "nb",
            "nb-rNO",
            "nl",
            "pl",
            "pt",
            "pt-rBR",
            "ro",
            "ru",
            "sl",
            "sk",
            "sr",
            "b+sr+Latn",
            "sv",
            "sw",
            "ta",
            "th",
            "tr",
            "uk",
            "vi",
            "zh",
            "zh-rHK",
            "zh-rMO",
            "zh-rTW",
        )
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

androidComponents {
  onVariants(selector().withBuildType("debug")) { variant ->
    variant.packaging.jniLibs.useLegacyPackaging.set(true) // for HWAsan
    variant.packaging.jniLibs.keepDebugSymbols.add("**/liborganicmaps.so") // for debug syms
  }

  onVariants(selector().withBuildType("beta")) { variant ->
    variant.packaging.jniLibs.keepDebugSymbols.add("**/liborganicmaps.so") // for debug syms
  }
}

dependencies {
    implementation(project(":sdk"))
    coreLibraryDesugaring(libs.android.tools.desugar)

    // Google Play Location Services
    // TODO(@pastk): enabled via microG in all flavors,
    // so move google/java/app/organicmaps/location/* into main/ and remove symlinks.
    //
    // (Atlas: the location provider lives in src/fdroid, the only build left.)
    //
    // microG project's FOSS re-implementation of the proprietary libs.google.services.location
    implementation(libs.microg.services.location)
    implementation(libs.androidx.core)
    implementation(platform(libs.jetbrains.kotlin.bom))
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.car.app)
    implementation(libs.androidx.car.app.projected)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.documentfile)
    implementation(libs.android.material)
    // Fix for app/organicmaps/util/FileUploadWorker.java:14: error: cannot access ListenableFuture
    // https://github.com/organicmaps/organicmaps/issues/6106
    implementation(libs.google.guava)
    implementation(libs.appdevnext.androidchart)

    // The house About and Licenses pages (shared with the other apps): Compose on MMD.
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.mmd)

    // Test Dependencies
    androidTestImplementation(libs.androidx.test.junit)
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
}

android.applicationVariants.configureEach {
    val authorityValue = "$applicationId.provider"
    buildConfigField("String", "FILE_PROVIDER_AUTHORITY", "\"$authorityValue\"")
    getMergedFlavor().manifestPlaceholders["FILE_PROVIDER_PLACEHOLDER"] = authorityValue
    resValue("string", "app_id", applicationId)
}


tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:unchecked", "-Xlint:deprecation"))
}

// The Licenses page lists what the build actually packs: this plugin walks each variant's runtime
// dependencies and writes res/raw/aboutlibraries.json (one entry per artifact, with its POM's
// license). What Gradle cannot see (fonts, data sets, native code, desugaring) and license texts
// of our own choosing are in config/libraries and config/licenses. Offline: the build fetches
// nothing; the full texts come from res/raw and config/licenses. The app reads the file with
// shared-licences' LicencesJson.kt (no AboutLibraries runtime library).
aboutLibraries {
    offlineMode.set(true)
    collect {
        configPath.set(rootProject.file("config"))
        fetchRemoteLicense.set(false)
        fetchRemoteFunding.set(false)
        includePlatform.set(false)
    }
    export {
        excludeFields.addAll("funding")
    }
    library {
        mergePlatformArtifacts.set(true)
        // The page lists what the release APK carries. These four come in only through
        // debugImplementation (Compose's ui-tooling), so a debug build leaves them off too.
        exclusionPatterns.addAll(
            listOf(
                "androidx.compose.ui:ui-tooling", "androidx.compose.ui:ui-tooling-data",
                "androidx.compose.material:material", "androidx.compose.material:material-icons-core",
            ).map { Pattern.compile("^" + Regex.escape(it) + "$") },
        )
    }
}

// The build fails when a bundled library has no license, or a license with no full text to show
// (neither its own text from config/licenses nor a family text in res/raw). Mudita review, 4 Nov 2026.
val licenceRawTexts: List<String> = file("src/main/res/raw").listFiles().orEmpty().map { it.name.lowercase() }
tasks.matching { it.name.startsWith("prepareLibraryDefinitions") }.configureEach {
    val raw = licenceRawTexts
    inputs.property("licenceRawTexts", raw) // so the check reruns when a text is added or removed
    doLast {
        fun family(n: String): String? {
            val l = n.lowercase()
            return when {
                "classpath" in l -> "classpath"; "lgpl" in l || "lesser general" in l -> "lgpl"
                "gpl" in l || "general public licen" in l -> "gpl"; "unicode" in l -> "unicode"; "apache" in l -> "apache"
                "mpl" in l || "mozilla" in l -> "mpl"; "mit" in l.split(Regex("[^a-z]+")) -> "mit"; "bsd" in l -> "bsd"
                "ofl" in l || "open font" in l -> "ofl"; "cc-by" in l || "cc by" in l || "cc0" in l || "public domain" in l -> "none"
                else -> null
            }
        }
        val bad = mutableListOf<String>()
        outputs.files.asFileTree.matching { include("**/aboutlibraries.json") }.forEach { f ->
            @Suppress("UNCHECKED_CAST")
            val root = groovy.json.JsonSlurper().parse(f) as Map<String, Any?>
            val lics = (root["licenses"] as? Map<String, Map<String, Any?>>).orEmpty()
            (root["libraries"] as? List<Map<String, Any?>>).orEmpty().forEach { lib ->
                val keys = (lib["licenses"] as? List<String>).orEmpty()
                if (keys.isEmpty()) bad += "${lib["uniqueId"]}: no license"
                keys.forEach { k ->
                    val e = lics[k].orEmpty()
                    if ((e["content"] as? String).isNullOrBlank()) {
                        val fam = family((e["spdxId"] ?: e["name"] ?: k).toString())
                        if (fam == null || (fam != "none" && raw.none { fam in it }))
                            bad += "${lib["uniqueId"]}: no text for ${e["name"] ?: k}"
                    }
                }
            }
        }
        if (bad.isNotEmpty()) throw GradleException("Licenses page: add the text in config/licenses or res/raw: " + bad.distinct().joinToString("; "))
    }
}
