plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
}
kotlin {
    jvm("desktop")
    sourceSets {
        commonMain {
            dependencies {
                implementation(compose.desktop.common)
                implementation(compose.components.resources)
                implementation(compose.materialIconsExtended)
                implementation(compose.material3)

                implementation(libs.dorkbox)

                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)

                implementation("org.codehaus.plexus:plexus-classworlds:2.8.0")

                implementation("com.github.albfernandez:juniversalchardet:2.4.0")

                implementation(libs.sql.sqlite)
                implementation(libs.sql.postgresql)
                implementation(libs.sql.flyway)

                api(libs.exposed.core)
                api(libs.exposed.dao)
                api(libs.exposed.jdbc)
                api(libs.exposed.json)
                api(libs.exposed.datetime)
                implementation(libs.exposed.migration)

                api(libs.datascanner)

                implementation(libs.files.pdfbox)
                implementation(libs.files.fastexcel)
                implementation(libs.files.fastexcel.reader)
                implementation(libs.files.junrar)
                implementation(libs.files.poi.core)
                implementation(libs.files.poi.ooxml)
                implementation(libs.files.poi.scratchpad)
                implementation(libs.files.odftoolkit.java)
                implementation(libs.files.odftoolkit.simple)

                implementation(libs.logging.oshai)
                implementation(libs.logging.logback)

                api(libs.koin.core)
                api(libs.koin.compose)
                api(libs.koin.compose.viewmodel)
                api(libs.koin.compose.viewmodel.navigation)
                implementation(libs.lifecycle.viewmodel)

                api(libs.filekit.dialogs.compose)

                implementation(libs.kotlin.stdlib)

                implementation("org.bouncycastle:bcprov-jdk15on:1.70")
                implementation("org.bouncycastle:bcpkix-jdk15on:1.70")
                implementation(libs.aws.s3)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(compose.desktop.uiTestJUnit4)
                implementation(libs.koin.test)
                implementation(libs.koin.test.junit4)
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "ru.packetdima.datascanner.resources"
}