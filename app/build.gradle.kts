plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("org.jetbrains.kotlin.kapt") // Not in version catalog; add manually
}

import java.util.Properties

val versionPropsFile = file("version.properties")

// Version auto-increment logic

tasks.register("bumpVersion") {
    doLast {
        val props = Properties().apply {
            if (versionPropsFile.exists()) {
                versionPropsFile.inputStream().use { load(it) }
            }
        }
        var major = (props["major"] as String?)?.toIntOrNull() ?: 2
        var minor = (props["minor"] as String?)?.toIntOrNull() ?: 6
        var patch = (props["patch"] as String?)?.toIntOrNull() ?: 2

        patch++
        if (patch > 100) {
            patch = 1
            minor++
        }
        if (minor > 9) {
            minor = 0
            major++
        }
        props["major"] = major.toString()
        props["minor"] = minor.toString()
        props["patch"] = patch.toString()
        versionPropsFile.outputStream().use { props.store(it, null) }
        println("Version bumped to $major.$minor.$patch")
    }
}

tasks.named("preBuild").configure {
    dependsOn("bumpVersion")
}

val versionProps = Properties().apply {
    if (versionPropsFile.exists()) {
        versionPropsFile.inputStream().use { load(it) }
    }
}
val major = (versionProps["major"] as String?)?.toIntOrNull() ?: 2
val minor = (versionProps["minor"] as String?)?.toIntOrNull() ?: 6
val patch = (versionProps["patch"] as String?)?.toIntOrNull() ?: 2

android {
    namespace = "com.example.diaryapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.diaryapp"
        minSdk = 26
        targetSdk = 34
        versionCode = major * 10000 + minor * 100 + patch
        versionName = "$major.$minor.$patch"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
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

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
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

    // Room DB
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)

    // Rich Text Editor
    implementation(libs.richeditor)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    implementation("com.github.bumptech.glide:glide:4.16.0")
    kapt("com.github.bumptech.glide:compiler:4.16.0")

    implementation("de.hdodenhof:circleimageview:3.1.0")

    implementation("com.github.yalantis:ucrop:2.2.8")

    implementation("com.google.code.gson:gson:2.10.1")

    implementation("jp.wasabeef:blurry:4.0.1")
    implementation("com.google.android.gms:play-services-auth:20.7.0")
    
    // ViewPager2 for image viewer
    implementation("androidx.viewpager2:viewpager2:1.0.0")
}
