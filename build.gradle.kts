plugins {
    kotlin("jvm") version "2.1.20"
}

group = "io.github.skabyy"
version = "2.1.0"

repositories {
    maven("https://maven.aliyun.com/repository/public")
    mavenCentral()
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
}
