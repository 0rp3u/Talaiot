package com.cdsap.talaiot.publisher

import com.cdsap.talaiot.configuration.InfluxDbPublisherConfiguration
import com.cdsap.talaiot.entities.*


import com.cdsap.talaiot.logger.TestLogTrackerRecorder
import com.cdsap.talaiot.publisher.graphpublisher.KInfluxDBContainer
import io.kotlintest.Spec
import io.kotlintest.specs.BehaviorSpec
import org.influxdb.dto.Query
import java.util.concurrent.Executors


class InfluxDbPublisherTest : BehaviorSpec() {

    val container = KInfluxDBContainer().withAuthEnabled(false)

    override fun beforeSpec(spec: Spec) {
        super.beforeSpec(spec)
        container.start()
    }

    override fun afterSpec(spec: Spec) {
        super.afterSpec(spec)
        container.stop()
    }

    val influxDB by lazy {
        container.newInfluxDB
    }

    init {
        given("InfluxDbPublisher instance") {
            val logger = TestLogTrackerRecorder

            `when`("Simple configuration is provided") {
                val database = "talaiot"
                val influxDbConfiguration = InfluxDbPublisherConfiguration().apply {
                    dbName = database
                    url = container.url
                    taskMetricName = "task"
                    buildMetricName = "build"
                }
                val influxDbPublisher = InfluxDbPublisher(
                    influxDbConfiguration, logger, TestExecutor()
                )
                influxDbPublisher.publish(executionReport())
                then("task and build data is store in the database") {

                    val taskResultTask =
                        influxDB.query(Query("select *  from $database.rpTalaiot.task"))
                    val taskResultBuild =
                        influxDB.query(Query("select * from $database.rpTalaiot.build"))
                    assert(taskResultTask.results.isNotEmpty() && taskResultTask.results[0].series[0].name == "task")
                    assert(taskResultBuild.results.isNotEmpty() && taskResultBuild.results[0].series[0].name == "build")
                }
            }

            `when`("configuration defines just send build information") {
                val databaseNoTaskMetrics = "databaseWithoutTasks"
                val influxDbConfiguration = InfluxDbPublisherConfiguration().apply {
                    dbName = databaseNoTaskMetrics
                    url = container.url
                    taskMetricName = "task"
                    buildMetricName = "build"
                    publishTaskMetrics = false
                }
                val influxDbPublisher = InfluxDbPublisher(
                    influxDbConfiguration, logger, TestExecutor()
                )
                influxDbPublisher.publish(executionReport())
                then("database contains only build information") {
                    val taskResultTask =
                        influxDB.query(Query("select * from $databaseNoTaskMetrics.rpTalaiot.task"))
                    val taskResultBuild =
                        influxDB.query(Query("select * from $databaseNoTaskMetrics.rpTalaiot.build"))
                    assert(taskResultTask.results.isNotEmpty() && taskResultTask.results[0].series == null)
                    assert(taskResultBuild.results.isNotEmpty() && taskResultBuild.results[0].series[0].name == "build")
                }
            }
            `when`("configuration defines just send task information") {
                val databaseNoBuildMetrics = "databaseWithoutBuild"
                val influxDbConfiguration = InfluxDbPublisherConfiguration().apply {
                    dbName = databaseNoBuildMetrics
                    url = container.url
                    taskMetricName = "task"
                    buildMetricName = "build"
                    publishBuildMetrics = false
                }
                val influxDbPublisher = InfluxDbPublisher(
                    influxDbConfiguration, logger, TestExecutor()
                )
                influxDbPublisher.publish(executionReport())
                then("database contains only task information") {
                    val taskResultTask =
                        influxDB.query(Query("select * from $databaseNoBuildMetrics.rpTalaiot.task"))
                    val taskResultBuild =
                        influxDB.query(Query("select * from $databaseNoBuildMetrics.rpTalaiot.build"))
                    assert(taskResultTask.results.isNotEmpty() && taskResultTask.results[0].series[0].name == "task")
                    assert(taskResultBuild.results.isNotEmpty() && taskResultBuild.results[0].series == null)
                }
            }
            `when`("the execution report includes custom task metrics") {
                val databaseTaskMetrics = "databaseTaskBuild"
                val influxDbConfiguration = InfluxDbPublisherConfiguration().apply {
                    dbName = databaseTaskMetrics
                    url = container.url
                    taskMetricName = "task"
                    buildMetricName = "build"
                }
                val influxDbPublisher = InfluxDbPublisher(
                    influxDbConfiguration, logger, TestExecutor()
                )
                influxDbPublisher.publish(executionReport())
                then("database contains custom metrics linked to the task execution") {
                    val taskResult =
                        influxDB.query(Query("select value,state,module,rootNode,task,metric1,metric2 from $databaseTaskMetrics.rpTalaiot.task"))
                    val combinedTaskColumns =
                        taskResult.results.joinToString { it.series.joinToString { it.columns.joinToString() } }
                    assert(combinedTaskColumns == "time, value, state, module, rootNode, task, metric1, metric2")

                    val combinedTaskValues =
                        taskResult.results.joinToString { it.series.joinToString { it.values.joinToString() } }
                    assert(combinedTaskValues.matches("""\[.+, 1\.0, EXECUTED, app, false, :clean, value1, value2\]""".toRegex()))

                }
            }
            `when`("the execution report includes custom build metrics") {
                val databaseBuildMetrics = "databaseBuild"
                val influxDbConfiguration = InfluxDbPublisherConfiguration().apply {
                    dbName = databaseBuildMetrics
                    url = container.url
                    taskMetricName = "task"
                    buildMetricName = "build"
                }
                val influxDbPublisher = InfluxDbPublisher(
                    influxDbConfiguration, logger, TestExecutor()
                )
                influxDbPublisher.publish(executionReport())
                then("database contains custom metrics linked to the build execution") {

                    val buildResult =
                        influxDB.query(Query("select * from $databaseBuildMetrics.rpTalaiot.build"))

                    val combinedBuildColumns =
                        buildResult.results.joinToString { it.series.joinToString { it.columns.joinToString() } }
                    assert(combinedBuildColumns == "time, configuration, duration, metric3, metric4, success")

                    val combinedBuildValues =
                        buildResult.results.joinToString { it.series.joinToString { it.values.joinToString() } }
                    assert(combinedBuildValues.matches("""\[.+, 0\.0, 10\.0, value3, value4, true\]""".toRegex()))

                }
            }

            `when`("publishOnlyBuildMetrics is enabled ") {
                val databaseNoMetrics = "databaseWithoutTasks"
                val influxDbConfiguration = InfluxDbPublisherConfiguration().apply {
                    dbName = databaseNoMetrics
                    url = container.url
                    taskMetricName = "task"
                    buildMetricName = "build"
                    publishTaskMetrics = false
                }
                val influxDbPublisher = InfluxDbPublisher(
                    influxDbConfiguration, logger, TestExecutor()
                )

                then("build metrics are sent and task metrics doesn't") {
                    influxDbPublisher.publish(executionReport())

                    val buildResult =
                        influxDB.query(Query("select \"duration\",configuration,success from $databaseNoMetrics.rpTalaiot.build"))

                    val combinedBuildColumns =
                        buildResult.results.joinToString { it.series.joinToString { it.columns.joinToString() } }
                    assert(combinedBuildColumns == "time, duration, configuration, success")

                    val combinedBuildValues =
                        buildResult.results.joinToString { it.series.joinToString { it.values.joinToString() } }
                    assert(combinedBuildValues.matches("""\[.+, 10\.0, 0\.0, true\]""".toRegex()))

                    val taskResult = influxDB.query(Query("select value from $databaseNoMetrics.rpTalaiot.task"))

                    assert(taskResult.results[0].series == null)

                }
            }

        }
    }

}

private fun executionReport() = ExecutionReport(
    durationMs = "10",
    success = true,
    customProperties = CustomProperties(
        taskProperties = getMetricsTasks(),
        buildProperties = getMetricsBuild()
    ),

    tasks = listOf(
        TaskLength(
            1, "clean", ":clean", TaskMessageState.EXECUTED, false,
            "app", emptyList()
        )
    )
)

private fun completeExecutionReport() = ExecutionReport(
    durationMs = "10",
 //   beginMs = "10",
  //  endMs = "12",
    buildId = "12",
    buildInvocationId = "123",
    configurationDurationMs = "32",
    environment = Environment(
        cpuCount = "4",
        osVersion = "Linux 1.4",
        maxWorkers = "2",
        javaRuntime = "1.2",
        locale = "EN-us",
        username = "user",
        publicIp = "127.0.0.1",
        defaultChartset = "default",
        ideVersion = "2.1",
        gradleVersion = "6.2.2",
        cacheMode = "cacheMode",
        cachePushEnabled = "true",
        cacheUrl = "cacheUrl",
        cacheHit = "20",
        cacheMiss = "30",
        cacheStore = "10",
        gitBranch = "git_branch",
        gitUser = "git_user",
        switches = Switches(
            daemon = "true",
            offline = "true"
        ),
        hostname = "localMachine",
        osManufacturer = "osManufact4r"
    ),
    success = true,
    customProperties = CustomProperties(
        taskProperties = getMetricsTasks(),
        buildProperties = getMetricsBuild()
    ),

    tasks = listOf(
        TaskLength(
            1, "clean", ":clean", TaskMessageState.EXECUTED, false,
            "app", emptyList()
        )
    )
)

private fun getMetricsTasks(): MutableMap<String, String> {
    return mutableMapOf(
        "metric1" to "value1",
        "metric2" to "value2"
    )
}

private fun getMetricsBuild(): MutableMap<String, String> {
    return mutableMapOf(
        "metric3" to "value3",
        "metric4" to "value4"
    )
}
