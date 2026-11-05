
const { remote } = require("webdriverio");
const assert = require("node:assert/strict");

async function runTest() {
  // Step 1: Define the Sauce Labs capabilities
  const capabilities = {
    platformName: "Android",

    "appium:app":
      "storage:filename=Android-MyDemoAppRN.1.3.0.build-244.apk",

    "appium:deviceName": "Google.*",
    "appium:automationName": "UiAutomator2",

    "sauce:options": {
      appiumVersion: "latest",
      build: "appium-training-build-1",
      name: "My First Appium Test"
    }
  };

  let driver;
  let testPassed = false;

  try {
    // Step 2: Connect to Sauce Labs
    console.log("Connecting to Sauce Labs...");

    driver = await remote({
      protocol: "https",
      user: process.env.SAUCE_USERNAME,
      key: process.env.SAUCE_ACCESS_KEY,

      hostname: "ondemand.us-west-1.saucelabs.com",
      port: 443,
      path: "/wd/hub",

      capabilities
    });

    console.log("Connected successfully!");
    console.log("Session ID:", driver.sessionId);

    // Step 3: Verify the application launched
    console.log("Checking application...");

    const appPackage = await driver.getCurrentPackage();

    console.log("Current app package:", appPackage);

    // Step 4: Assert the correct app is running
    assert.equal(
      appPackage,
      "com.saucelabs.mydemoapp.rn",
      "The Sauce Labs Demo App should be running"
    );

    console.log("SUCCESS: Correct application is running!");

    // Step 5: Mark the test as passed
    testPassed = true;

  } catch (error) {
    console.error("TEST FAILED:", error.message);
    process.exitCode = 1;

  } finally {
    // Step 6: Report the result and close the session
    if (driver) {
      try {
        await driver.execute(
          "sauce:job-result=" +
          (testPassed ? "passed" : "failed")
        );
      } finally {
        await driver.deleteSession();
        console.log("Sauce Labs session ended.");
      }
    }
  }
}

runTest().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
