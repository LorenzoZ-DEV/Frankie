plugins {
    id("java-library")
    id("com.gradleup.shadow") version "9.2.2"
    id("xyz.jpenilla.run-velocity") version "3.0.2"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.aikar.co/content/groups/aikar/")
    maven("https://repo.nickuc.com/maven-releases/")
}

dependencies {
    compileOnly("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")
    compileOnly("net.luckperms:api:5.4")
    // Gia' fornita dal proxy (velocity-api la porta con se'): non va shadata.
    compileOnly("com.google.code.gson:gson:2.13.2")

    implementation("co.aikar:acf-velocity:0.5.1-SNAPSHOT")
    implementation("com.zaxxer:HikariCP:6.3.0")
    implementation("com.mysql:mysql-connector-j:9.3.0")
    implementation("org.yaml:snakeyaml:2.4")

    // nLogin API
    compileOnly("com.nickuc.login:api:10.4")

    // compileOnly non finisce sul classpath dei test: va ridichiarato.
    testImplementation("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks {
    test {
        useJUnitPlatform()
    }

    runVelocity {
        // Configure the Velocity version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        velocityVersion("3.5.0-SNAPSHOT")
    }

    shadowJar {
        archiveClassifier.set("")
        relocate("co.aikar.commands", "dev.lorenzz.frankie.libs.acf")
        relocate("co.aikar.locales", "dev.lorenzz.frankie.libs.locales")
        relocate("com.zaxxer.hikari", "dev.lorenzz.frankie.libs.hikari")
        relocate("org.yaml.snakeyaml", "dev.lorenzz.frankie.libs.snakeyaml")
    }

    build {
        dependsOn(shadowJar)
    }

    processResources {
        val props = mapOf("version" to version)
        filesMatching("velocity-plugin.json") {
            expand(props)
        }
    }
}