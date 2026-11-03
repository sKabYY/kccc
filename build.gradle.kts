plugins {
    kotlin("jvm") version "2.1.20"
    `maven-publish`
    signing
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

// ---------------------------------------------------------------------------
// 发布到 Maven Central（Central Portal）
//
// 构建产物：
//   kccc-2.1.0.jar         主 jar
//   kccc-2.1.0-sources.jar  源码 jar（Central 要求）
//   kccc-2.1.0-javadoc.jar  文档 jar（Central 要求）
//   kccc-2.1.0.pom          POM（含全部必需元数据）
//   *.asc                   GPG 签名（Central 要求）
//
// 凭据只从 ~/.gradle/gradle.properties 或 ORG_GRADLE_PROJECT_* 读取，
// 绝不进入版本库。
// ---------------------------------------------------------------------------

// kotlin("jvm") 只有 Java 源码才会产出 javadoc。本项目是纯 Kotlin，
// 生成的 javadoc jar 为空，Central 会拒绝空包，因此放入 README 兜底。
java {
    withSourcesJar()
    withJavadocJar()
}

tasks.named<Jar>("javadocJar") {
    from(rootProject.file("README.md"))
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            pom {
                name.set("Kccc")
                description.set(
                    "Kccc (Kotlin Compiler Compiler Combinator) is a Parsec-style parser " +
                        "combinator runtime for Kotlin/JVM. It compiles declarative .grammar files " +
                        "into executable parser trees, with zero third-party runtime dependencies."
                )
                inceptionYear.set("2019")
                url.set("https://github.com/sKabYY/kccc")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        id.set("sKabYY")
                        name.set("sKabYY")
                        url.set("https://github.com/sKabYY")
                    }
                }
                scm {
                    url.set("https://github.com/sKabYY/kccc")
                    connection.set("scm:git:git://github.com/sKabYY/kccc.git")
                    developerConnection.set("scm:git:ssh://git@github.com/sKabYY/kccc.git")
                }
            }
        }
    }

    repositories {
        // 本地暂存仓库：Central Portal 需要把它打成 zip 上传
        maven {
            name = "staging"
            url = uri(layout.buildDirectory.dir("staging-repo"))
        }
    }
}

// 没有 GPG 私钥时不签名，保证 build / publishToMavenLocal 不被阻塞；
// 配好私钥后发布自动签名。
signing {
    val secretKey = providers.gradleProperty("signingInMemoryKey").orNull
    val rawKeyId = providers.gradleProperty("signingInMemoryKeyId").orNull
    val password = providers.gradleProperty("signingInMemoryKeyPassword").orNull
    val hasKey = secretKey != null || providers.gradleProperty("signing.keyId").isPresent

    if (secretKey != null) {
        // Gradle 9.x 只接受 8 位短 keyId（如 00B5050F / 0x00B5050F），
        // 传完整 40 位指纹会报 "The key ID must be in a valid form"，
        // 这里统一归一化，容忍用户直接复制指纹。
        val shortKeyId = rawKeyId?.removePrefix("0x")?.takeLast(8)
        // password 传 null 会导致 signatory 注册失败，必须给空串
        useInMemoryPgpKeys(shortKeyId, secretKey, password ?: "")
    }
    if (hasKey) {
        sign(publishing.publications)
    }
}

// 打包成 Central Portal 可直接上传的 bundle：zip 根目录即 io/github/... 路径
tasks.register<Zip>("bundleForCentral") {
    group = "publishing"
    description = "打包 staging 仓库为 Maven Central Portal 可上传的 zip"
    dependsOn("publishAllPublicationsToStagingRepository")
    from(layout.buildDirectory.dir("staging-repo"))
    // maven-metadata.xml 是 Gradle 为本地仓库生成的，Central 会自行维护索引，不需要上传
    exclude("**/maven-metadata.xml*")
    archiveFileName.set("kccc-${version}-bundle.zip")
    destinationDirectory.set(layout.buildDirectory.dir("central-bundle"))
}
