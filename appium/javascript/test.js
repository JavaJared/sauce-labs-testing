
const { remote } = require("webdriverio");
const assert = require("node:assert/strict");

async function runTest() {
  const driver = await remote({
    hostname: "127.0.0.1",
    port: 4723,
    path: "/",
    logLevel: "info",
    capabilities: {
      platformName: "Android",
      "appium:automationName": "UiAutomator2",
      "appium:deviceName": "Android Emulator",
      "appium:appPackage": "com.android.settings",
      "appium:appActivity": ".Settings",
      "appium:noReset": true
    }
  });

  try {
    const packageName = await driver.getCurrentPackage();

    assert.equal(packageName, "com.android.settings");

    console.log("PASS: Android Settings opened");
  } finally {
    await driver.deleteSession();
  }
}

runTest().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
