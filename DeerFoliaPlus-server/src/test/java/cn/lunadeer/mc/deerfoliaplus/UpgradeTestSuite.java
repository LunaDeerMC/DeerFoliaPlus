package cn.lunadeer.mc.deerfoliaplus;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

@Suite
@SuiteDisplayName("DeerFoliaPlus upstream upgrade regression tests")
@SelectPackages("cn.lunadeer.mc.deerfoliaplus.upgrade")
@ConfigurationParameter(key = "TestSuite", value = "VanillaFeature")
public class UpgradeTestSuite {
}
