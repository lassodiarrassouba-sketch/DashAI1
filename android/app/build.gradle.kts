import java.util.Properties

plugins {
    id("com.android.application")
}

val releasePropertiesFile = rootProject.file("keystore.properties")
val releaseProperties = Properties().apply {
    if (releasePropertiesFile.isFile) {
        releasePropertiesFile.inputStream().use { load(it) }
    }
}

fun releaseSecret(name: String): String? {
    return (releaseProperties[name] as String?) ?: providers.environmentVariable(name).orNull
}

val uploadStoreFile = releaseSecret("DASHAI_UPLOAD_STORE_FILE")
val uploadStorePassword = releaseSecret("DASHAI_UPLOAD_STORE_PASSWORD")
val uploadKeyAlias = releaseSecret("DASHAI_UPLOAD_KEY_ALIAS")
val uploadKeyPassword = releaseSecret("DASHAI_UPLOAD_KEY_PASSWORD")
val productionEndpoint = releaseSecret("DASHAI_PROD_API_ENDPOINT") ?: ""
val debugEndpoint = releaseSecret("DASHAI_DEBUG_API_ENDPOINT") ?: "http://172.20.10.4:8000/api/ask"
val hasReleaseSigning = listOf(
    uploadStoreFile,
    uploadStorePassword,
    uploadKeyAlias,
    uploadKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "com.dashai.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dashai.app"
        minSdk = 23
        targetSdk = 36
        versionCode = 220
        versionName = "2.2.0"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(uploadStoreFile!!)
                storePassword = uploadStorePassword
                keyAlias = uploadKeyAlias
                keyPassword = uploadKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            // Le suffixe permet d'installer l'APK de test à côté de la version DIASCO déjà signée.
            applicationIdSuffix = ".thinkdiagtest"
            versionNameSuffix = "-test"
            // Autorise HTTP uniquement dans les APK debug pour tester un backend local.
            // Le workflow ThinkDiag injecte cependant l'URL HTTPS du backend existant.
            manifestPlaceholders["usesCleartextTraffic"] = "true"
            resValue("string", "default_backend_endpoint", debugEndpoint)
        }
        release {
            // Ne publiez pas une version release qui contacte un backend HTTP.
            // Déployez le backend derrière HTTPS et utilisez une URL https://...
            manifestPlaceholders["usesCleartextTraffic"] = "false"
            resValue("string", "default_backend_endpoint", productionEndpoint)
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("com.google.android.material:material:1.13.0")
    testImplementation("junit:junit:4.13.2")
}
