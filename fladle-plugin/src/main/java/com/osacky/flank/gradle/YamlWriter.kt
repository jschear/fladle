package com.osacky.flank.gradle

import org.gradle.internal.impldep.com.google.common.annotations.VisibleForTesting

internal class YamlWriter {

  internal fun createConfigProps(config: FladleConfig, base: FlankGradleExtension): String {
    if (base.projectId.orNull == null) {
      check(base.serviceAccountCredentials.isPresent) { "ServiceAccountCredentials in fladle extension not set. https://runningcode.github.io/fladle/configuration/#serviceaccountcredentials" }
    }
    check(base.debugApk.isPresent) { "debugApk must be specified" }
    if (!config.sanityRobo.get()) {
      check(config.instrumentationApk.isPresent xor config.roboScript.hasValue) {
        val prefix = if (base.instrumentationApk.isPresent && config.roboScript.hasValue) {
          "Both instrumentationApk file and roboScript file were specified, but only one is expected."
        } else {
          "Must specify either a instrumentationApk file or a roboScript file."
        }
        """
        $prefix
        instrumentationApk=${config.instrumentationApk.orNull}
        roboScript=${config.roboScript.orNull}
        """.trimIndent()
      }
    }

    val additionalProperties = writeAdditionalProperties(config)
    val flankProperties = writeFlankProperties(config)

    return buildString {
      appendln("gcloud:")
      appendln("  app: ${config.debugApk.get()}")
      if (config.instrumentationApk.hasValue) {
        appendln("  test: ${config.instrumentationApk.get()}")
      }
      if (config.devices.isPresentAndNotEmpty) appendln(createDeviceString(config.devices.get()))
      appendln(additionalProperties)
      append(flankProperties)
    }
  }

  internal fun writeFlankProperties(config: FladleConfig): String = buildString {
    appendln("flank:")

    // Fladle will fail in configuration phase if both maxTestShards and testShards are present
    appendProperty(config.maxTestShards, name = "max-test-shards")
    appendProperty(config.testShards, name = "max-test-shards")

    appendProperty(config.shardTime, name = "shard-time")
    appendProperty(config.repeatTests, name = "num-test-runs")
    appendProperty(config.smartFlankGcsPath, name = "smart-flank-gcs-path")
    appendProperty(config.projectId, name = "project")
    appendProperty(config.keepFilePath, name = "keep-file-path")
    appendListProperty(config.filesToDownload, name = "files-to-download") { appendln("  - $it") }
    if (!config.sanityRobo.get()) {
      appendListProperty(config.additionalTestApks, name = "additional-app-test-apks") { appendln("    $it") }
    }
    appendProperty(config.runTimeout, name = "run-timeout")
    appendProperty(config.ignoreFailedTests, name = "ignore-failed-tests")
    appendProperty(config.disableSharding, name = "disable-sharding")
    appendProperty(config.smartFlankDisableUpload, name = "smart-flank-disable-upload")
    appendProperty(config.localResultsDir, name = "local-result-dir")
    appendListProperty(config.testTargetsAlwaysRun, name = "test-targets-always-run") { appendln("  - class $it") }
    appendProperty(config.legacyJunitResult, name = "legacy-junit-result")
    appendProperty(config.fullJunitResult, name = "full-junit-result")
    appendProperty(config.outputStyle, name = "output-style")
    appendProperty(config.defaultTestTime, name = "default-test-time")
    appendProperty(config.defaultClassTestTime, name = "default-class-test-time")
    appendProperty(config.useAverageTestTimeForNewTests, name = "use-average-test-time-for-new-tests")
    appendProperty(config.disableResultsUpload, name = "disable-results-upload")
    appendListProperty(config.testTargetsForShard, name = "test-targets-for-shard") {
      appendln("    - $it")
    }
    appendAdditionalProperty(config.additionalFlankOptions)
  }

  internal fun writeAdditionalProperties(config: FladleConfig): String = buildString {
    appendProperty(config.useOrchestrator, name = "use-orchestrator")
    appendProperty(config.autoGoogleLogin, name = "auto-google-login")
    appendProperty(config.recordVideo, name = "record-video")
    appendProperty(config.performanceMetrics, name = "performance-metrics")
    appendProperty(config.testTimeout, name = "timeout")
    if (config.async.isPresent && config.async.get()) {
      appendProperty(config.async, name = "async")
    }
    appendProperty(config.resultsHistoryName, name = "results-history-name")
    appendProperty(config.resultsBucket, name = "results-bucket")
    appendMapProperty(config.environmentVariables, name = "environment-variables") {
      appendln("    ${it.key}: ${it.value}")
    }
    appendListProperty(config.testTargets, name = "test-targets") { appendln("  - $it") }
    appendListProperty(config.directoriesToPull, name = "directories-to-pull") { appendln("  - $it") }
    appendProperty(config.flakyTestAttempts, name = "num-flaky-test-attempts")
    appendProperty(config.resultsDir, name = "results-dir")
    appendProperty(config.testRunnerClass, name = "test-runner-class")
    appendProperty(config.numUniformShards, name = "num-uniform-shards")
    appendMapProperty(config.clientDetails, name = "client-details") { appendln("    ${it.key}: ${it.value}") }
    appendMapProperty(config.otherFiles, name = "other-files") { appendln("    ${it.key}: ${it.value}") }
    appendProperty(config.networkProfile, name = "network-profile")
    if (!config.sanityRobo.get()) {
      appendProperty(config.roboScript, name = "robo-script")
      appendListProperty(config.roboDirectives, name = "robo-directives") {
        val value = it.getOrElse(2) { "" }.let { stringValue -> if (stringValue.isBlank()) "\"\"" else stringValue }
        appendln("    ${it[0]}:${it[1]}: $value")
      }
    }
    appendListProperty(config.additionalApks, name = "additional-apks") { appendln("    - $it") }
    appendProperty(config.grantPermissions, name = "grant-permissions")
    appendProperty(config.type, name = "type")
    appendListProperty(config.scenarioLabels, name = "scenario-labels") { appendln("    - $it") }
    appendListProperty(config.scenarioNumbers, name = "scenario-numbers") { appendln("    - $it") }
    appendListProperty(config.obbFiles, name = "obb-files") { appendln("    - $it") }
    appendListProperty(config.obbNames, name = "obb-names") { appendln("    - $it") }
    appendListProperty(config.testTargetsForShard, name = "test-targets-for-shard") { appendln("    - $it") }
    appendProperty(config.failFast, name = "fail-fast")
    appendAdditionalProperty(config.additionalGcloudOptions)
  }

  @VisibleForTesting
  internal fun createDeviceString(devices: List<Map<String, String>>): String = buildString {
    appendln("  device:")
    for (device in devices) {
      if (device["model"] == null) throw RequiredDeviceKeyMissingException("model")
      val model = device["model"]
      if (device["version"] == null) throw RequiredDeviceKeyMissingException("version")
      val version = device["version"]
      val orientation = device["orientation"]
      val locale = device["locale"]
      appendln("  - model: $model")
      appendln("    version: $version")
      orientation?.let {
        appendln("    orientation: $it")
      }
      locale?.let {
        appendln("    locale: $it")
      }
    }
  }
}
