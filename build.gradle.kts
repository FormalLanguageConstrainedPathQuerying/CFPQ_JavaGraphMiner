plugins {
    java
    kotlin("jvm") version "1.9.21"
}

group = "me.pointsto.graph"
version = "1.0-SNAPSHOT"

val jacoDbVersion: String by rootProject

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.jetbrains.kotlin:kotlin-test")

    api(group = "org.jacodb", name = "jacodb-core", version = jacoDbVersion)
    api(group = "org.jacodb", name = "jacodb-analysis", version = jacoDbVersion)
    api(group = "org.jacodb", name = "jacodb-approximations", version = jacoDbVersion)
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}