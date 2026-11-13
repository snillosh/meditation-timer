plugins { id("com.android.application"); id("org.jetbrains.kotlin.plugin.compose") }
android {
 namespace = "uk.co.meditationtimer"
 compileSdk = 36
 defaultConfig { applicationId = "uk.co.meditationtimer"; minSdk = 31; targetSdk = 36; versionCode = 1; versionName = "1.0"; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
 buildFeatures { compose = true }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
 implementation(platform("androidx.compose:compose-bom:2026.02.00"))
 implementation("androidx.activity:activity-compose:1.12.4")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.ui:ui-tooling-preview")
 implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
 implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
 implementation("androidx.datastore:datastore-preferences:1.2.0")
 implementation("androidx.core:core-ktx:1.17.0")
 testImplementation("junit:junit:4.13.2")
 androidTestImplementation("androidx.test.ext:junit:1.3.0")
 androidTestImplementation("androidx.test:runner:1.7.0")
 androidTestImplementation(platform("androidx.compose:compose-bom:2026.02.00"))
 androidTestImplementation("androidx.compose.ui:ui-test-junit4")
 // Compose transitively selects Espresso 3.5, whose hidden input API was removed on API 37.
 androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
 debugImplementation("androidx.compose.ui:ui-test-manifest")
}
