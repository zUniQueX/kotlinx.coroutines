import java.net.URI

/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

internal val Settings.kotlinDevRepoUrl: Provider<String?>
    get() = providers
        .gradleProperty("kotlin_repo_url")
        .forUseAtConfigurationTime()
        .map { it.toString() }
        .orElse("")


fun RepositoryHandler.addKotilnRepository() = if (kotlinDevRepoUrl.get() != "") {
    println("DEBUG1")
    val passedUri = java.net.URI(kotlinDevRepoUrl.get())
    val artfRepo = MavenArtifactRepository(passedUri)
    this.addLast(artfRepo)
}else {}


// Main configuration

println("DEBUG3")

if (kotlinDevRepoUrl.get() != "") {
    logger.info("Setting kotlin development repository for settings in ${settingsDir.absolutePath}")

    pluginManagement.repositories.addKotilnRepository()
    buildscript.repositories.addKotilnRepository()

    gradle.beforeProject {
        buildscript.repositories.addKotilnRepository()
        repositories.addKotilnRepository()
    }
}
