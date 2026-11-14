plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Gallery: bundle every examples/*.json that has exactly 20 frames into assets/gallery/.
val galleryDir = layout.buildDirectory.dir("generated/gallery")
val generateGallery = tasks.register("generateGallery") {
    val src = rootProject.file("examples")
    val out = galleryDir.get().asFile
    inputs.dir(src)
    outputs.dir(out)
    doLast {
        val target = File(out, "gallery").apply { deleteRecursively(); mkdirs() }
        src.listFiles { f -> f.extension == "json" }?.sortedBy { it.name }?.forEach { f ->
            val json = groovy.json.JsonSlurper().parse(f) as Map<*, *>
            val frames = ((json["image"] as? Map<*, *>)?.get("frames") as? List<*>)?.size ?: 0
            if (frames == 20) f.copyTo(File(target, f.name), overwrite = true)
        }
    }
}

android {
    namespace = "app.spotled.gui"
    compileSdk = 35
    defaultConfig {
        applicationId = "app.spotled.gui"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }
    signingConfigs {
        create("dev") {
            storeFile = rootProject.file("spotled-debug.keystore")
            storePassword = "android"; keyAlias = "spotled"; keyPassword = "android"
        }
    }
    buildTypes {
        debug { signingConfig = signingConfigs.getByName("dev") }
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    sourceSets { getByName("main") { assets.srcDir(galleryDir) } }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(bom)
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

tasks.configureEach { if (name.startsWith("merge") && name.endsWith("Assets")) dependsOn(generateGallery) }
