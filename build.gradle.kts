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

/**
 * Regenerates the TypeScript conformance fixtures under `ts/`.
 * See `ts/README.md`: the generated files are committed, so any behavior
 * change on this side shows up as a diff in the TypeScript package too.
 */
tasks.register<JavaExec>("generateTsFixtures") {
    group = "verification"
    description = "Regenerate the TypeScript conformance fixtures under ts/"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("io.github.skabyy.kccc.conformance.GenerateTsFixturesKt")
    workingDir = projectDir
    outputs.dir(layout.projectDirectory.dir("ts/conformance"))
    outputs.file(layout.projectDirectory.file("ts/src/yccc-grammar.ts"))
}
