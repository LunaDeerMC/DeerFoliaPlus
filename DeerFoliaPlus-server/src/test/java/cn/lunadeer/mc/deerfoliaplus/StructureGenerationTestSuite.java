package cn.lunadeer.mc.deerfoliaplus;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

@Suite
@SuiteDisplayName("Configurable vanilla structure generation repairs")
@SelectPackages({"cn.lunadeer.mc.deerfoliaplus.worldgen", "cn.lunadeer.mc.deerfoliaplus.configurations"})
@ConfigurationParameter(key = "TestSuite", value = "VanillaFeature")
public class StructureGenerationTestSuite {
}
