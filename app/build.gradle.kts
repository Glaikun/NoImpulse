plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
}

android {
    namespace = "com.glaikun.noimpulse"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.glaikun.noimpulse"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "1.0.2"

        // Swaps in HiltTestApplication so app/src/androidTest can launch @AndroidEntryPoint
        // activities under Hilt (see HiltTestRunner.kt).
        testInstrumentationRunner = "com.glaikun.noimpulse.HiltTestRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            // Robolectric needs app resources/themes on the classpath to render real
            // Compose UI (ui/NoImpulseContent.kt) in the integration suite under app/src/test.
            isIncludeAndroidResources = true
        }
    }

    // Known flake (AGP/KSP incremental-cache quirk, not a real regression — see CLAUDE.md's
    // "Build environment" section): running `lintDebug` a second time right after a `clean`
    // can fail. A normal (non-clean) build/lint run is unaffected, and so is CI, since every
    // CI run starts from a fresh checkout.
    lint {
        // Grandfathers in every finding that existed when Lint was re-enabled, so this
        // doesn't fail CI on old debt. Regenerate after intentionally fixing/adding findings
        // with: ./gradlew updateLintBaseline
        baseline = file("lint-baseline.xml")
    }
}

// Unit tests run against the debug variant only. The integration suite's
// createComposeRule() needs the compose-test host activity, which ui-test-manifest
// merges into the *debug* app manifest via debugImplementation; the release manifest
// must not ship a test activity, so under testReleaseUnitTest the launch fails
// (RoboMonitoringInstrumentation: "Unable to resolve activity"). The suite has no
// build-type-specific logic, so the release run added no coverage — only duplication.
androidComponents {
    beforeVariants(selector().withBuildType("release")) {
        it.hostTests[com.android.build.api.variant.HostTestBuilder.UNIT_TEST_TYPE]?.enable = false
    }
}

detekt {
    // No custom config/detekt/detekt.yml — the bundled default ruleset already matches this
    // project's readability bar, and skipping it is one less file to keep in sync across
    // detekt upgrades. Baseline grandfathers in pre-existing findings; regenerate after
    // intentionally fixing/adding findings with: ./gradlew detektBaseline
    baseline = file("config/detekt/baseline.xml")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    ksp(libs.hilt.compiler)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.core)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
