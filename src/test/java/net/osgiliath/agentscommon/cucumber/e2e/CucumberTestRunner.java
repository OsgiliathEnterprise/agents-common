package net.osgiliath.agentscommon.cucumber.e2e;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/**
 * JUnit Platform Suite runner for Cucumber BDD tests.
 * <p>
 * This runner will:
 * - Execute all .feature files discovered from the 'features' package
 * - Use step definitions from the 'net.osgiliath.codeprompt.cucumber.steps' package
 * - Generate pretty console output and JSON reports
 */
@Suite
@IncludeEngines("cucumber")
@SelectPackages("features/e2e")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "net.osgiliath.agentscommon.cucumber.e2e")
@ConfigurationParameter(
        key = PLUGIN_PROPERTY_NAME,
        value = "pretty, html:build/reports/cucumber/cucumber.html, json:build/reports/cucumber/cucumber.json"
)
public class CucumberTestRunner {
    // This class serves as the entry point for running Cucumber tests
    // No implementation needed - annotations drive the test execution
}

