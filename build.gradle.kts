plugins {
    java
    application
    kotlin("jvm") version "1.9.21"
    jacoco
}

group = "me.cfpq.pointsto.miner"
version = "1.0-SNAPSHOT"

val jacoDbVersion: String by rootProject
val slf4jVersion: String by rootProject
val kotlinLoggingVersion: String by rootProject

val hibernateVersion: String by project
val springBootVersion: String by project
val guavaVersion: String by project
val commonsLangVersion: String by project
val commonsIoVersion: String by project
val junitVersion: String by project
val jacksonVersion: String by project
val mockitoVersion: String by project
val gsonVersion: String by project

application {
    mainClass.set("me.cfpq.pointsto.miner.MainKt")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(group = "org.jacodb", name = "jacodb-core", version = jacoDbVersion)
    implementation(group = "org.jacodb", name = "jacodb-analysis", version = jacoDbVersion)
    implementation(group = "org.jacodb", name = "jacodb-approximations", version = jacoDbVersion)
    implementation(group =  "org.slf4j", name = "slf4j-simple", version = slf4jVersion)
    implementation(group = "io.github.microutils", name = "kotlin-logging", version = kotlinLoggingVersion)

    // For some future analysis it may be needed to use a separate `ClassLoader` for libs under analysis,
    // but right now graph mining doesn't necessarily require it, so for the sake of simplicity it's not used. 
    runtimeOnly("com.google.guava:guava:$guavaVersion")
    runtimeOnly("org.apache.commons:commons-lang3:$commonsLangVersion")
    runtimeOnly("commons-io:commons-io:$commonsIoVersion")
    runtimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
    runtimeOnly("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    runtimeOnly("org.mockito:mockito-core:$mockitoVersion")
    runtimeOnly("com.google.code.gson:gson:$gsonVersion")

    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitVersion")
}

kotlin {
    jvmToolchain(19)
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
    maxHeapSize = "5g"
}


tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        html.required.set(true)
    }
}
