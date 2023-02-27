/*
 *  Copyright (c) 2020, 2021 Microsoft Corporation
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Microsoft Corporation - initial API and implementation
 *       Fraunhofer Institute for Software and Systems Engineering - added dependencies
 *       ZF Friedrichshafen AG - add dependency
 *
 */

plugins {
    `java-library`
    id("application")
    id("com.github.johnrengelman.shadow") version "8.0.0"
}

dependencies {
    implementation(project(":core:control-plane:control-plane-core"))

    implementation(project(":extensions:common:http"))

    implementation(project(":extensions:common:configuration:configuration-filesystem"))
    implementation(project(":extensions:common:iam:iam-mock"))

    implementation(project(":extensions:common:auth:auth-tokenbased"))
    implementation(project(":extensions:control-plane:api:management-api"))

    implementation(project(":data-protocols:ids"))

    implementation(project(":samples:04.1-file-transfer-listener:listener"))
}

application {
    mainClass.set("org.eclipse.edc.boot.system.runtime.BaseRuntime")
}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    exclude("**/pom.properties", "**/pom.xml")
    mergeServiceFiles()
    archiveFileName.set("consumer.jar")
}
