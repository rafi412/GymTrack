import java.util.Properties

val localProperties = Properties().apply {
    load(rootProject.file("local.properties").inputStream())
}

val apiKey = localProperties.getProperty("API_KEY") ?: throw GradleException("API_KEY no definida en local.properties")


plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services")
    alias(libs.plugins.androidx.navigation.safeargs.kotlin)
    id("org.jetbrains.kotlin.plugin.serialization") version libs.versions.kotlin.get()
}

android {
    namespace = "com.example.gymtrack"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.gymtrack"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "API_KEY", "\"$apiKey\"")

        android.buildFeatures.buildConfig = true // Habilitar la generación de BuildConfig
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation(platform(libs.firebase.bom))

    // Firebase (usando catálogo y BoM)
    implementation(platform(libs.firebase.bom)) // Plataforma BoM
    implementation(libs.firebase.auth.ktx)      // Auth KTX (sin versión)
    implementation(libs.firebase.firestore.ktx) // Firestore KTX (sin versión)
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.72")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")

    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    //API IA
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")

    //Serializacion de json
    implementation(libs.kotlinx.serialization.json)

    implementation ("com.github.bumptech.glide:glide:4.15.1")

    implementation("com.google.code.gson:gson:2.10.1")// Usa la última versión estable

}
