package com.profiletailors.smp.bdd.postgres

import org.junit.platform.suite.api.ConfigurationParameter
import org.junit.platform.suite.api.IncludeEngines
import org.junit.platform.suite.api.SelectClasspathResource
import org.junit.platform.suite.api.Suite

@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(
    key = "cucumber.glue",
    value = "com.profiletailors.smp.bdd.glue,com.profiletailors.smp.bdd.postgres",
)
@ConfigurationParameter(
    key = "cucumber.execution.parallel.enabled",
    value = "false",
)
class CucumberPostgresIntegrationTest
