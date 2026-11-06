
/**
 * Sauce Labs My Demo App - iOS Tests
 *
 * Framework: Appium + WebdriverIO
 * Platform: iOS Real Devices
 * Cloud Provider: Sauce Labs
 *
 * Available tests:
 * - catalog
 * - product
 * - cart
 *
 * Run:
 * node sauce-ios-demo-tests.js catalog
 * node sauce-ios-demo-tests.js product
 * node sauce-ios-demo-tests.js cart
 * node sauce-ios-demo-tests.js all
 */

const { remote } = require("webdriverio");
const assert = require("node:assert/strict");

// ==========================================
// 1. CONFIGURATION
// ==========================================

const TEST = process.argv[2] || "catalog";

const TIMEOUT = 20000;

const APP =
  "storage:filename=iOS-Real-Device-MyRNDemoApp.ipa";

// iOS accessibility selector
const sel = (name) => `~${name}`;

// Require environment variables
const required = (key) => {
  if (!process.env[key]) {
    throw new Error(
      `Set ${key} before running`
    );
  }

  return process.env[key];
};

// ==========================================
// 2. HELPER FUNCTIONS
// ==========================================

// Wait for an element to be visible
async function shown(driver, name) {
  const element = await driver.$(sel(name));

  await element.waitForDisplayed({
    timeout: TIMEOUT
  });

  return element;
}

// Tap an element
async function tap(driver, name) {
  const element = await shown(driver, name);
  await element.click();
}

// Open the first product
async function openFirstProduct(driver) {
  await shown(driver, "products screen");

  const products = await driver.$$(
    sel("store item")
  );

  assert.ok(
    products.length > 0,
    "Expected at least one catalog product"
  );

  await products[0].click();

  await shown(driver, "product screen");
}

// Swipe upward on the device
async function swipeUp(driver) {
  const { width, height } =
    await driver.getWindowRect();

  await driver.performActions([
    {
      type: "pointer",
      id: "finger1",

      parameters: {
        pointerType: "touch"
      },

      actions: [
        {
          type: "pointerMove",
          duration: 0,
          x: Math.round(width / 2),
          y: Math.round(height * 0.75)
        },
        {
          type: "pointerDown",
          button: 0
        },
        {
          type: "pause",
          duration: 100
        },
        {
          type: "pointerMove",
          duration: 500,
          x: Math.round(width / 2),
          y: Math.round(height * 0.30)
        },
        {
          type: "pointerUp",
          button: 0
        }
      ]
    }
  ]);

  await driver.releaseActions();
}

// Scroll until an element is visible
async function tapScrolled(driver, name) {
  for (let i = 0; i < 5; i++) {
    const element = await driver.$(
      sel(name)
    );

    if (
      await element
        .isDisplayed()
        .catch(() => false)
    ) {
      await element.click();
      return;
    }

    await swipeUp(driver);
  }

  throw new Error(
    `Cannot find visible element: ${name}`
  );
}

// ==========================================
// 3. TEST CASES
// ==========================================

const tests = {

  // ----------------------------------------
  // TEST 1: PRODUCT CATALOG
  // ----------------------------------------

  async catalog(driver) {
    console.log(
      "Testing iOS product catalog..."
    );

    // Verify catalog loads
    await shown(
      driver,
      "products screen"
    );

    // Find the available products
    const products = await driver.$$(
      sel("store item")
    );

    // Verify products exist
    assert.ok(
      products.length > 0,
      "Product catalog should not be empty"
    );

    console.log(
      "PASS: Product catalog loaded"
    );
  },

  // ----------------------------------------
  // TEST 2: PRODUCT DETAILS
  // ----------------------------------------

  async product(driver) {
    console.log(
      "Testing iOS product details..."
    );

    // Open the first product
    await openFirstProduct(driver);

    // Locate product price
    const price = await shown(
      driver,
      "product price"
    );

    // Verify backpack price
    assert.equal(
      (await price.getText()).trim(),
      "$29.99"
    );

    // Verify product description
    const description = await shown(
      driver,
      "product description"
    );

    assert.match(
      await description.getText(),
      /carry\.allTheThings/i
    );

    // Verify Add to Cart is available
    // without modifying the shopping cart
    const addButton = await driver.$(
      sel("Add To Cart button")
    );

    assert.ok(
      await addButton.isExisting(),
      "Add To Cart button should exist"
    );

    console.log(
      "PASS: Product details validated"
    );
  },

  // ----------------------------------------
  // TEST 3: SHOPPING CART
  // ----------------------------------------

  async cart(driver) {
    console.log(
      "Testing iOS shopping cart..."
    );

    // Open first product
    await openFirstProduct(driver);

    // Add product to cart
    await tapScrolled(
      driver,
      "Add To Cart button"
    );

    // Allow cart animation to finish
    await driver.pause(750);

    // Open Cart tab
    await tap(
      driver,
      "tab bar option cart"
    );

    // Verify cart screen
    await shown(
      driver,
      "cart screen"
    );

    // Verify correct product
    const product = await shown(
      driver,
      "product label"
    );

    assert.equal(
      (await product.getText()).trim(),
      "Sauce Labs Backpack"
    );

    // Remove product
    await tap(
      driver,
      "remove item"
    );

    // Verify empty cart
    await shown(
      driver,
      "Go Shopping button"
    );

    console.log(
      "PASS: Shopping cart validated"
    );
  }
};

// ==========================================
// 4. SAUCE LABS TEST EXECUTION
// ==========================================

async function run(name) {
  let driver;
  let passed = false;

  try {
    console.log(
      `Starting iOS test: ${name}`
    );

    // Connect to Sauce Labs
    driver = await remote({
      protocol: "https",

      hostname:
        "ondemand.us-west-1.saucelabs.com",

      port: 443,
      path: "/wd/hub",

      user: required("SAUCE_USERNAME"),
      key: required("SAUCE_ACCESS_KEY"),

      capabilities: {
        platformName: "iOS",

        "appium:automationName":
          "XCUITest",

        "appium:app": APP,

        "appium:deviceName":
          process.env.SAUCE_IOS_DEVICE ||
          "iPhone.*",

        "sauce:options": {
          appiumVersion: "latest",

          build:
            "RN-Demo-iOS-JavaScript",

          name: `iOS JS: ${name}`
        }
      }
    });

    console.log(
      "Sauce Labs Session:",
      driver.sessionId
    );

    // Run selected test
    await tests[name](driver);

    passed = true;

    console.log(
      `PASS: ${name}`
    );

  } catch (error) {
    console.error(
      `FAIL: ${name}`,
      error.message
    );

    process.exitCode = 1;

    // Capture screenshot on failure
    if (driver) {
      await driver.saveScreenshot(
        `failed-ios-${name}.png`
      ).catch(() => {});
    }

  } finally {
    if (driver) {

      try {
        // Report test status
        await driver.execute(
          `sauce:job-result=${
            passed ? "passed" : "failed"
          }`
        );

      } catch (error) {
        console.error(
          "Failed to update Sauce Labs status:",
          error.message
        );

        process.exitCode = 1;

      } finally {
        // Release remote device
        await driver.deleteSession();
      }
    }
  }
}

// ==========================================
// 5. MAIN PROGRAM
// ==========================================

(async () => {

  const selectedTests =
    TEST === "all"
      ? Object.keys(tests)
      : [TEST];

  for (const name of selectedTests) {

    if (!tests[name]) {
      throw new Error(
        `Unknown test: ${name}`
      );
    }

    await run(name);
  }

})().catch(error => {
  console.error(error.message);
  process.exitCode = 1;
});
