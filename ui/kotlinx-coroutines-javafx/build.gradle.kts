/*
 * Copyright 2016-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("org.openjfx.javafxplugin") version "0.0.14"
}

configurations {
    register("javafx")
    named("compileOnly") {
        extendsFrom(configurations["javafx"])
    }
    named("testImplementation") {
        extendsFrom(configurations["javafx"])
    }
}

javafx {
    version = version("javafx")
    modules = listOf("javafx.controls")
    configuration = "javafx"
}

// Fixup moduleplugin in order to properly run with classpath
tasks {
    test {
        extensions.configure(org.javamodularity.moduleplugin.extensions.TestModuleOptions::class) {
            addReads["kotlinx.coroutines.javafx"] = "kotlin.test,test.utils.jvm"
            addReads["test.utils.jvm"] = "junit,kotlin.test"
        }
    }
}
