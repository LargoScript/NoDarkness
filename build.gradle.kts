plugins {
    java
}

group = "com.largoscript.nodarkness"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

val necesseJar: String? = (project.findProperty("necesseJar") as String?)
    ?: System.getenv("NECESSE_JAR")

logger.lifecycle("necesseJar property: ${project.findProperty("necesseJar")}")
logger.lifecycle("NECESSE_JAR env: ${System.getenv("NECESSE_JAR")}")
logger.lifecycle("Final necesseJar value: $necesseJar")

repositories {
    mavenCentral()
    flatDir {
        dirs("libs")  // CustomSettingsLib
    }
    if (necesseJar != null) {
        val jarFile = file(necesseJar)
        if (jarFile.exists()) {
            flatDir {
                dirs(jarFile.parentFile)
            }
            logger.lifecycle("Added flatDir repository: ${jarFile.parentFile.absolutePath}")
        }
    }
}

dependencies {
    // CustomSettingsLib
    val customSettingsLib = file("libs/CustomSettingsLib.jar")
    if (customSettingsLib.exists()) {
        compileOnly(files(customSettingsLib))
        logger.lifecycle("Added CustomSettingsLib to dependencies")
    } else {
        logger.warn("⚠️  CustomSettingsLib.jar not found in libs/. Please download it from https://github.com/AizSave/CustomSettingsLib")
    }
    
    if (necesseJar == null) {
        logger.warn("❗ Не задано шлях до Necesse.jar. Задайте властивість necesseJar або змінну середовища NECESSE_JAR.")
    } else {
        val jarFile = file(necesseJar)
        logger.lifecycle("Adding Necesse.jar to dependencies: ${jarFile.absolutePath}")
        logger.lifecycle("File exists: ${jarFile.exists()}")
        compileOnly(files(jarFile))
    }
}

// Додаємо jar до compile classpath після налаштування dependencies
afterEvaluate {
    if (necesseJar != null) {
        val jarFile = file(necesseJar)
        if (jarFile.exists()) {
            sourceSets.main.get().compileClasspath += files(jarFile)
            logger.lifecycle("Added Necesse.jar to compile classpath in afterEvaluate: ${jarFile.absolutePath}")
        }
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    doFirst {
        if (necesseJar != null) {
            val jarFile = file(necesseJar)
            if (jarFile.exists()) {
                classpath += files(jarFile)
                logger.lifecycle("Added Necesse.jar to JavaCompile classpath: ${jarFile.absolutePath}")
            }
        }
    }
}

tasks.jar {
    archiveFileName.set("no-darkness.jar")
    from("mod.info")
    // Включаємо папку resources з preview.png та локалізацією
    from("src/main/resources") {
        into("resources")
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.register<Copy>("install") {
    val modsDir = project.findProperty("modsDir") as String?
        ?: System.getenv("NECESSE_MODS_DIR")
        ?: (System.getProperty("user.home") + File.separator + "AppData" + File.separator + "Roaming" + File.separator + "Necesse" + File.separator + "mods")

    dependsOn(tasks.jar)
    from(tasks.jar)
    into(modsDir)
    doLast {
        logger.lifecycle("Мод встановлено в: $modsDir")
    }
}

tasks.register<Copy>("installDevMod") {
    val devModDir = file("../NoDarkness_DevMod")
    
    dependsOn(tasks.jar)
    from(tasks.jar)
    into(devModDir)
    
    // Копіюємо preview.png якщо він є
    val previewFile = file("src/main/resources/preview.png")
    if (previewFile.exists()) {
        from(previewFile)
        into(devModDir)
    }
    
    doLast {
        logger.lifecycle("✅ Мод скопійовано в DevMod папку: ${devModDir.absolutePath}")
    }
}
