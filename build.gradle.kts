// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.secrets) apply false
  alias(libs.plugins.google.services) apply false
}

tasks.register("exportProjectZip") {
    group = "distribution"
    description = "Builds latest debug APK and creates SmartBusinessManager_Export.zip with SmartBusinessManager.apk and README_INSTALL_BN.txt directly at root"
    dependsOn(":app:assembleDebug")
    notCompatibleWithConfigurationCache("Requires project root directory walk and zip packaging")

    doLast {
        val apkFile = file("app/build/outputs/apk/debug/app-debug.apk")
        if (!apkFile.exists()) {
            throw GradleException("Build Error: APK file was not found at ${apkFile.absolutePath}")
        }
        val apkSize = apkFile.length()
        if (apkSize < 1000000L) {
            throw GradleException("Build Error: Generated APK is suspiciously small (${apkSize} bytes). Build may be corrupted.")
        }

        // 1. Prepare Export directory
        val exportDir = file("SmartBusinessManager_Export")
        if (!exportDir.exists()) {
            exportDir.mkdirs()
        }
        val apkInExportDir = file("SmartBusinessManager_Export/SmartBusinessManager.apk")
        apkFile.copyTo(apkInExportDir, overwrite = true)

        val readmeFile = file("README_INSTALL_BN.txt")
        val readmeInExportDir = file("SmartBusinessManager_Export/README_INSTALL_BN.txt")
        if (readmeFile.exists()) {
            readmeFile.copyTo(readmeInExportDir, overwrite = true)
        }

        // Copy directly to root and APK/
        val rootApk = file("SmartBusinessManager.apk")
        apkFile.copyTo(rootApk, overwrite = true)
        val hishabpatiApk = file("SmartHishabpati.apk")
        apkFile.copyTo(hishabpatiApk, overwrite = true)

        val apkDir = file("APK")
        if (!apkDir.exists()) apkDir.mkdirs()
        val apkInApkDir = file("APK/SmartBusinessManager.apk")
        apkFile.copyTo(apkInApkDir, overwrite = true)
        val hishabpatiInApkDir = file("APK/SmartHishabpati.apk")
        apkFile.copyTo(hishabpatiInApkDir, overwrite = true)

        val releaseDir = file("release-files")
        if (!releaseDir.exists()) releaseDir.mkdirs()
        apkFile.copyTo(file("release-files/SmartBusinessManager.apk"), overwrite = true)
        apkFile.copyTo(file("release-files/SmartHishabpati.apk"), overwrite = true)

        // 2. Generate pure SmartBusinessManager_Export.zip (containing SmartBusinessManager.apk and README_INSTALL_BN.txt)
        val exportZipFile = file("SmartBusinessManager_Export.zip")
        if (exportZipFile.exists()) {
            exportZipFile.delete()
        }
        val zipOutExport = java.util.zip.ZipOutputStream(java.io.FileOutputStream(exportZipFile))
        try {
            val apkEntry = java.util.zip.ZipEntry("SmartBusinessManager.apk")
            zipOutExport.putNextEntry(apkEntry)
            apkFile.inputStream().use { it.copyTo(zipOutExport) }
            zipOutExport.closeEntry()

            if (readmeFile.exists()) {
                val readmeEntry = java.util.zip.ZipEntry("README_INSTALL_BN.txt")
                zipOutExport.putNextEntry(readmeEntry)
                readmeFile.inputStream().use { it.copyTo(zipOutExport) }
                zipOutExport.closeEntry()
            }
        } finally {
            zipOutExport.close()
        }

        // 3. Generate full SmartBusinessManager_Project.zip for complete source export
        val projectZipFile = file("SmartBusinessManager_Project.zip")
        if (projectZipFile.exists()) {
            projectZipFile.delete()
        }
        val zipOutProj = java.util.zip.ZipOutputStream(java.io.FileOutputStream(projectZipFile))
        try {
            val apkEntry = java.util.zip.ZipEntry("APK/SmartBusinessManager.apk")
            zipOutProj.putNextEntry(apkEntry)
            apkFile.inputStream().use { it.copyTo(zipOutProj) }
            zipOutProj.closeEntry()

            if (readmeFile.exists()) {
                val readmeEntry = java.util.zip.ZipEntry("README_BN.txt")
                zipOutProj.putNextEntry(readmeEntry)
                readmeFile.inputStream().use { it.copyTo(zipOutProj) }
                zipOutProj.closeEntry()
            }

            val ignoredDirs = setOf("build", ".gradle", ".git", "release-files", "APK", "SmartBusinessManager_Export")
            val ignoredFiles = setOf("SmartBusinessManager_Project.zip", "SmartBusinessManager_Export.zip", "SmartBusinessManager.apk", "backup_before_sync_fix.zip")

            rootDir.walkTopDown()
                .filter { f ->
                    val relPath = f.relativeTo(rootDir).path
                    val parts = relPath.split(java.io.File.separatorChar)
                    !parts.any { it in ignoredDirs } && f.name !in ignoredFiles
                }
                .forEach { f ->
                    val relPath = f.relativeTo(rootDir).path
                    if (relPath.isNotEmpty() && relPath != ".") {
                        val entryPath = "SmartBusinessManager_Project/" + relPath.replace('\\', '/')
                        if (f.isDirectory) {
                            val dirEntry = java.util.zip.ZipEntry(if (entryPath.endsWith("/")) entryPath else "$entryPath/")
                            zipOutProj.putNextEntry(dirEntry)
                            zipOutProj.closeEntry()
                        } else {
                            val fileEntry = java.util.zip.ZipEntry(entryPath)
                            zipOutProj.putNextEntry(fileEntry)
                            f.inputStream().use { it.copyTo(zipOutProj) }
                            zipOutProj.closeEntry()
                        }
                    }
                }
        } finally {
            zipOutProj.close()
        }

        // Copy to AI Studio artifact directory if present
        val artifactDir = file(".aistudio/artifacts/brain/36747399-67e5-4e69-a403-bbb6e8d9524a")
        if (artifactDir.exists()) {
            exportZipFile.copyTo(java.io.File(artifactDir, "SmartBusinessManager_Export.zip"), overwrite = true)
            rootApk.copyTo(java.io.File(artifactDir, "SmartBusinessManager.apk"), overwrite = true)
        }

        // Verify SmartBusinessManager_Export.zip
        val zipVerify = java.util.zip.ZipFile(exportZipFile)
        val apkInside = zipVerify.getEntry("SmartBusinessManager.apk")
        val readmeInside = zipVerify.getEntry("README_INSTALL_BN.txt")
        val zipSizeMb = exportZipFile.length() / (1024 * 1024)

        if (apkInside == null) {
            zipVerify.close()
            throw GradleException("Verification Error: SmartBusinessManager.apk was not found inside SmartBusinessManager_Export.zip")
        }
        val insideApkSize = apkInside.size
        zipVerify.close()

        println("==================================================")
        println("Export Successful!")
        println("ZIP Path: ${exportZipFile.absolutePath}")
        println("ZIP Size: $zipSizeMb MB (${exportZipFile.length()} bytes)")
        println("Verified: SmartBusinessManager.apk ($insideApkSize bytes) directly inside ZIP!")
        println("Verified: README_INSTALL_BN.txt directly inside ZIP!")
        println("Verified: Direct APK exported at ${rootApk.absolutePath}")
        println("==================================================")
    }
}

tasks.register("exportZip") {
    group = "distribution"
    description = "Alias for exportProjectZip"
    dependsOn("exportProjectZip")
}

