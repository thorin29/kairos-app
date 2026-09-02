// Top-level build file. Plugins are declared here (apply false) and applied in
// the module that needs them, so versions live in one place (the version catalog).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
