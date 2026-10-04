plugins {
    id("com.android.application") version "8.9.1" apply false
    id("org.jetbrains.kotlin.android") version "2.1.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.20" apply false
}

// aapt2 mmap()s resources and fails on virtiofs/9p/NFS trees. Set `secondwind.buildRoot`
// (e.g. in ~/.gradle/gradle.properties) to a local-disk directory to build out of tree.
providers.gradleProperty("secondwind.buildRoot").orNull?.let { root ->
    allprojects { layout.buildDirectory.set(file("$root/${rootProject.name}/${project.name}")) }
}
