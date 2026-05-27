package com.profiletailors.smp.bdd.fast

import org.junit.platform.suite.api.ConfigurationParameter
import org.junit.platform.suite.api.IncludeEngines
import org.junit.platform.suite.api.SelectClasspathResource
import org.junit.platform.suite.api.Suite

@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(
    key = "cucumber.glue",
    value = "com.profiletailors.smp.bdd.glue,com.profiletailors.smp.bdd.fast",
)
class CucumberFastIntegrationTest
