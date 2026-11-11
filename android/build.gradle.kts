import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.aboutlibraries.android) apply false
}

fun run(cmd: List<String>): String = providers.exec { commandLine = cmd }.standardOutput.asText.get().trim()

val isWindows = DefaultNativePlatform.getCurrentOperatingSystem().isWindows
val bash = if (isWindows) "C:\\Program Files\\Git\\bin\\bash.exe" else "bash"
rootProject.ext["versionCode"] = Integer.parseInt(run(listOf(bash, "../tools/unix/version.sh", "android_code")))
// Atlas carries its own version name, raised by hand for each release. The version code stays
// CoMaps' date code (yyMMdd + the day's commit count): parts of the map core read the date from it.
rootProject.ext["versionName"] = "0.1.4"
