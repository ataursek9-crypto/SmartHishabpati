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
        val standardApk = file("app/build/outputs/apk/debug/app-debug.apk")
        val containerApk = file(".build-outputs/app-debug.apk")
        val apkFile = when {
            standardApk.exists() -> standardApk
            containerApk.exists() -> {
                standardApk.parentFile.mkdirs()
                containerApk.copyTo(standardApk, overwrite = true)
                standardApk
            }
            else -> standardApk
        }
        if (!apkFile.exists()) {
            throw GradleException("Build Error: APK file was not found at ${apkFile.absolutePath} or ${containerApk.absolutePath}")
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

        // 3. Generate comprehensive SmartHishabpati_Project.zip containing SmartHishabpati.apk, README, and full source code
        val hishabpatiProjectZip = file("SmartHishabpati_Project.zip")
        if (hishabpatiProjectZip.exists()) {
            hishabpatiProjectZip.delete()
        }
        val zipOutHishabpati = java.util.zip.ZipOutputStream(java.io.FileOutputStream(hishabpatiProjectZip))
        val addedZipEntries = mutableSetOf<String>()
        try {
            // (1) Include SmartHishabpati.apk directly at the root of the zip
            val rootApkEntry = java.util.zip.ZipEntry("SmartHishabpati.apk")
            zipOutHishabpati.putNextEntry(rootApkEntry)
            apkFile.inputStream().use { it.copyTo(zipOutHishabpati) }
            zipOutHishabpati.closeEntry()
            addedZipEntries.add("SmartHishabpati.apk")

            // (2) Include SmartHishabpati.apk inside APK/ directory
            val apkSubEntry = java.util.zip.ZipEntry("APK/SmartHishabpati.apk")
            zipOutHishabpati.putNextEntry(apkSubEntry)
            apkFile.inputStream().use { it.copyTo(zipOutHishabpati) }
            zipOutHishabpati.closeEntry()
            addedZipEntries.add("APK/SmartHishabpati.apk")

            // (3) Include README_INSTALL_BN.txt directly at root
            if (readmeFile.exists()) {
                val readmeEntry = java.util.zip.ZipEntry("README_INSTALL_BN.txt")
                zipOutHishabpati.putNextEntry(readmeEntry)
                readmeFile.inputStream().use { it.copyTo(zipOutHishabpati) }
                zipOutHishabpati.closeEntry()
                addedZipEntries.add("README_INSTALL_BN.txt")
            }

            // (4) Package all source files and project configurations
            val ignoredDirs = setOf("build", ".gradle", ".git", "release-files", "SmartBusinessManager_Export", ".build-outputs", ".aistudio")
            val ignoredFileNames = setOf(
                "SmartHishabpati_Project.zip", "SmartBusinessManager_Project.zip", "SmartBusinessManager_Export.zip",
                "SmartBusinessManager.apk", "SmartHishabpati.apk", "backup_before_sync_fix.zip"
            )

            rootDir.walkTopDown()
                .filter { f ->
                    val relPath = f.relativeTo(rootDir).path
                    val parts = relPath.split(java.io.File.separatorChar)
                    !parts.any { it in ignoredDirs } && f.name !in ignoredFileNames
                }
                .forEach { f ->
                    val relPath = f.relativeTo(rootDir).path
                    if (relPath.isNotEmpty() && relPath != ".") {
                        val entryPath = relPath.replace('\\', '/')
                        if (f.isDirectory) {
                            val dirEntryPath = if (entryPath.endsWith("/")) entryPath else "$entryPath/"
                            if (addedZipEntries.add(dirEntryPath)) {
                                val dirEntry = java.util.zip.ZipEntry(dirEntryPath)
                                zipOutHishabpati.putNextEntry(dirEntry)
                                zipOutHishabpati.closeEntry()
                            }
                        } else {
                            if (addedZipEntries.add(entryPath)) {
                                val fileEntry = java.util.zip.ZipEntry(entryPath)
                                zipOutHishabpati.putNextEntry(fileEntry)
                                f.inputStream().use { it.copyTo(zipOutHishabpati) }
                                zipOutHishabpati.closeEntry()
                            }
                        }
                    }
                }
        } finally {
            zipOutHishabpati.close()
        }

        // Copy legacy SmartBusinessManager_Project.zip alias
        val legacyProjectZip = file("SmartBusinessManager_Project.zip")
        hishabpatiProjectZip.copyTo(legacyProjectZip, overwrite = true)

        // Copy to AI Studio artifact directory if present
        val currentArtifactDir = file(".aistudio/artifacts/brain/617614b6-7fff-4024-8467-8451fe7798f8")
        if (!currentArtifactDir.exists()) {
            currentArtifactDir.mkdirs()
        }
        exportZipFile.copyTo(java.io.File(currentArtifactDir, "SmartBusinessManager_Export.zip"), overwrite = true)
        hishabpatiProjectZip.copyTo(java.io.File(currentArtifactDir, "SmartHishabpati_Project.zip"), overwrite = true)
        hishabpatiProjectZip.copyTo(java.io.File(currentArtifactDir, "SmartBusinessManager_Project.zip"), overwrite = true)
        rootApk.copyTo(java.io.File(currentArtifactDir, "SmartBusinessManager.apk"), overwrite = true)
        rootApk.copyTo(java.io.File(currentArtifactDir, "SmartHishabpati.apk"), overwrite = true)

        val activeArtifactDir = file(".aistudio/artifacts/brain/71aa39de-3ce2-4dbf-828f-52f94b28e9d0")
        if (activeArtifactDir.exists()) {
            exportZipFile.copyTo(java.io.File(activeArtifactDir, "SmartBusinessManager_Export.zip"), overwrite = true)
            hishabpatiProjectZip.copyTo(java.io.File(activeArtifactDir, "SmartHishabpati_Project.zip"), overwrite = true)
            rootApk.copyTo(java.io.File(activeArtifactDir, "SmartHishabpati.apk"), overwrite = true)
        }

        val sessionArtifactDir = file(".aistudio/artifacts/brain/e8285bc1-6151-4a56-bd72-a2eb065f4e7e")
        if (!sessionArtifactDir.exists()) {
            sessionArtifactDir.mkdirs()
        }
        exportZipFile.copyTo(java.io.File(sessionArtifactDir, "SmartBusinessManager_Export.zip"), overwrite = true)
        hishabpatiProjectZip.copyTo(java.io.File(sessionArtifactDir, "SmartHishabpati_Project.zip"), overwrite = true)
        hishabpatiProjectZip.copyTo(java.io.File(sessionArtifactDir, "SmartBusinessManager_Project.zip"), overwrite = true)
        rootApk.copyTo(java.io.File(sessionArtifactDir, "SmartBusinessManager.apk"), overwrite = true)
        rootApk.copyTo(java.io.File(sessionArtifactDir, "SmartHishabpati.apk"), overwrite = true)

        // Verification: inspect SmartHishabpati_Project.zip
        val zipVerifyProj = java.util.zip.ZipFile(hishabpatiProjectZip)
        val apkAtRoot = zipVerifyProj.getEntry("SmartHishabpati.apk")
        val apkInSub = zipVerifyProj.getEntry("APK/SmartHishabpati.apk")
        val readmeInZip = zipVerifyProj.getEntry("README_INSTALL_BN.txt")
        val projZipSize = hishabpatiProjectZip.length()

        if (apkAtRoot == null) {
            zipVerifyProj.close()
            throw GradleException("Verification Error: SmartHishabpati.apk was not found inside SmartHishabpati_Project.zip!")
        }
        if (apkAtRoot.size != apkSize) {
            val sizeGot = apkAtRoot.size
            zipVerifyProj.close()
            throw GradleException("Verification Error: APK inside ZIP size ($sizeGot bytes) does not match original APK size ($apkSize bytes)!")
        }
        zipVerifyProj.close()

        println("==================================================")
        println("Export & Packaging Verified Successfully!")
        println("Direct APK: ${hishabpatiApk.absolutePath} ($apkSize bytes)")
        println("Project ZIP: ${hishabpatiProjectZip.absolutePath} ($projZipSize bytes)")
        println("Verified: SmartHishabpati.apk ($apkSize bytes) is present directly inside ZIP!")
        println("Verified: README_INSTALL_BN.txt is present inside ZIP!")
        println("==================================================")
    }
}

tasks.register("exportZip") {
    group = "distribution"
    description = "Alias for exportProjectZip"
    dependsOn("exportProjectZip")
}

