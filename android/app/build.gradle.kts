plugins {
    id("com.android.application")
}

val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "com.martinb.millionos"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.martinb.millionos"
        minSdk = 24
        targetSdk = 34
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    // Release key: the workflow decodes the MILLIONOS_KEYSTORE_B64 secret into millionos.jks and passes the
    // password as an env var, so every new APK installs over the previous one. Never commit the key or its password.
    val keystore = file("millionos.jks")
    signingConfigs {
        create("release") {
            storeFile = keystore
            storePassword = System.getenv("MILLIONOS_KEYSTORE_PASSWORD")
            keyAlias = "millionos"
            keyPassword = System.getenv("MILLIONOS_KEYSTORE_PASSWORD")
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            // Pull requests build without the secret: they get the debug key and are never published.
            signingConfig = signingConfigs.getByName(if (keystore.exists()) "release" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}
