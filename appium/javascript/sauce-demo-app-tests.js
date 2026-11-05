
/**
 * Sauce Labs My Demo App React Native v1.3.0
 * Android Real Device Automated Tests
 *
 * Install:
 * npm install --save-dev webdriverio
 *
 * Required environment variables:
 * SAUCE_USERNAME
 * SAUCE_ACCESS_KEY
 *
 * Run individual tests:
 * node sauce-demo-app-tests.js catalog
 * node sauce-demo-app-tests.js product
 * node sauce-demo-app-tests.js cart
 * node sauce-demo-app-tests.js locked
 * node sauce-demo-app-tests.js validation
 * node sauce-demo-app-tests.js checkout
 *
 * Run all tests:
 * node sauce-demo-app-tests.js all
 */

const { remote } = require("webdriverio");
const assert = require("node:assert/strict");

// ==================================================
// 1. SAUCE LABS CONFIGURATION
// ==================================================

const APP =
  "storage:filename=Android-MyDemoAppRN.1.3.0.build-244.apk";

const RUN = process.argv[2] || "catalog";
const timeout = 20000;

// Android accessibility selector
const id = (name) => `//*[@content-desc="${name}"]`;

function config(testName) {
  return {
    protocol: "https",
    hostname: "ondemand.us-west-1.saucelabs.com",
    port: 443,
    path: "/wd/hub",

    user: process.env.SAUCE_USERNAME,
    key: process.env.SAUCE_ACCESS_KEY,

    logLevel: "warn",

    capabilities: {
      platformName: "Android",

      "appium:app": APP,
      "appium:deviceName": "Google.*",
      "appium:automationName": "UiAutomator2",

      "sauce:options": {
        appiumVersion: "latest",
        build: "RN-Demo-App-Training",
        name: testName
      }
    }
  };
}

// ==================================================
// 2. HELPER FUNCTIONS
// ==================================================

// Wait for an element to become visible
async function visible(driver, name) {
  const el = await driver.$(id(name));

  await el.waitForDisplayed({
    timeout
  });

  return el;
}

// Tap an element
async function tap(driver, name) {
  const el = await visible(driver, name);
  await el.click();
}

// Open the first product in the catalog
async function firstProduct(driver) {
  await visible(driver, "products screen");

  const products = await driver.$$(id("store item"));

  assert.ok(
    products.length > 0,
    "Catalog should contain products"
  );

  await products[0].click();

  await visible(driver, "product screen");
}

// Swipe upward
async function swipeUp(driver) {
  const { width, height } = await driver.getWindowRect();

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
          y: Math.round(height * 0.78)
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
          duration: 550,
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

// Find an element, scrolling if necessary
async function findVisibleWithScroll(
  driver,
  name,
  maxSwipes = 5
) {
  for (let i = 0; i <= maxSwipes; i++) {
    const el = await driver.$(id(name));

    if (await el.isDisplayed().catch(() => false)) {
      return el;
    }

    if (i < maxSwipes) {
      await swipeUp(driver);
    }
  }

  throw new Error(
    `Could not see '${name}' after ${maxSwipes} swipes`
  );
}

// Type into an input field
async function type(driver, name, value) {
  const el = await findVisibleWithScroll(
    driver,
    name
  );

  await el.setValue(value);

  // Hide keyboard to avoid obstructing buttons
  await driver.hideKeyboard().catch(() => {});
}

// Add the Sauce Labs Backpack to the cart
async function addBackpack(driver, qty = 1) {
  await firstProduct(driver);

  const price = await visible(
    driver,
    "product price"
  );

  assert.equal(
    (await price.getText()).trim(),
    "$29.99"
  );

  // Increase product quantity if requested
  for (let i = 1; i < qty; i++) {
    await tap(driver, "counter plus button");
  }

  // Add product to cart
  const addButton = await findVisibleWithScroll(
    driver,
    "Add To Cart button"
  );

  await addButton.click();

  // Allow the cart badge animation to complete
  await driver.pause(900);

  // Open cart
  await tap(driver, "cart badge");

  await visible(driver, "cart screen");

  // Confirm correct product was added
  const label = await visible(
    driver,
    "product label"
  );

  assert.equal(
    (await label.getText()).trim(),
    "Sauce Labs Backpack"
  );
}

// ==================================================
// 3. TEST CASES
// ==================================================

const tests = {

  // ------------------------------------------------
  // TEST 1: PRODUCT CATALOG
  // ------------------------------------------------

  async catalog(driver) {
    console.log("Testing product catalog...");

    await visible(driver, "products screen");

    const products = await driver.$$(
      id("store item")
    );

    assert.ok(
      products.length > 0,
      `Expected visible products, found ${products.length}`
    );

    const names = await driver.$$(
      id("store item text")
    );

    const texts = await Promise.all(
      names.map(el => el.getText())
    );

    assert.ok(
      texts.includes("Sauce Labs Backpack"),
      "Backpack is present in catalog"
    );

    console.log("Catalog test passed!");
  },

  // ------------------------------------------------
  // TEST 2: PRODUCT DETAILS
  // ------------------------------------------------

  async product(driver) {
    console.log("Testing product details...");

    await firstProduct(driver);

    const price = await visible(
      driver,
      "product price"
    );

    const description = await findVisibleWithScroll(
      driver,
      "product description"
    );

    // Verify backpack price
    assert.equal(
      (await price.getText()).trim(),
      "$29.99"
    );

    // Verify product description
    assert.match(
      await description.getText(),
      /carry\.allTheThings/i
    );

    // Verify Add to Cart button exists
    await findVisibleWithScroll(
      driver,
      "Add To Cart button"
    );

    console.log("Product details test passed!");
  },

  // ------------------------------------------------
  // TEST 3: SHOPPING CART
  // ------------------------------------------------

  async cart(driver) {
    console.log("Testing shopping cart...");

    // Add one backpack
    await addBackpack(driver, 1);

    // Check initial quantity
    const quantity = await visible(
      driver,
      "counter amount"
    );

    assert.match(
      await quantity.getText(),
      /1/
    );

    // Increase quantity
    await tap(
      driver,
      "counter plus button"
    );

    // Verify quantity is now 2
    const updatedQuantity = await visible(
      driver,
      "counter amount"
    );

    assert.match(
      await updatedQuantity.getText(),
      /2/
    );

    // Remove the product entirely
    await tap(driver, "remove item");

    // Verify empty-cart button appears
    await visible(
      driver,
      "Go Shopping button"
    );

    console.log("Shopping cart test passed!");
  },

  // ------------------------------------------------
  // TEST 4: LOCKED-OUT USER LOGIN
  // ------------------------------------------------

  async locked(driver) {
    console.log("Testing locked-out login...");

    // Add product and proceed to checkout
    await addBackpack(driver);

    await tap(
      driver,
      "Proceed To Checkout button"
    );

    await visible(driver, "login screen");

    // Enter locked-out demo credentials
    await type(
      driver,
      "Username input field",
      "alice@example.com"
    );

    await type(
      driver,
      "Password input field",
      "10203040"
    );

    // Attempt login
    await tap(driver, "Login button");

    // Verify correct error message
    const message = await driver.$(
      '//*[@text="Sorry, this user has been locked out."]'
    );

    await message.waitForDisplayed({
      timeout
    });

    assert.equal(
      await message.isDisplayed(),
      true
    );

    console.log("Locked-out login test passed!");
  },

  // ------------------------------------------------
  // TEST 5: EMPTY LOGIN VALIDATION
  // ------------------------------------------------

  async validation(driver) {
    console.log("Testing login validation...");

    // Navigate to login through checkout
    await addBackpack(driver);

    await tap(
      driver,
      "Proceed To Checkout button"
    );

    await visible(driver, "login screen");

    // Submit without credentials
    await tap(driver, "Login button");

    // Verify username validation message
    const error = await driver.$(
      '//*[@text="Username is required"]'
    );

    await error.waitForDisplayed({
      timeout
    });

    assert.equal(
      await error.isDisplayed(),
      true
    );

    console.log("Login validation test passed!");
  },

  // ------------------------------------------------
  // TEST 6: COMPLETE CHECKOUT FLOW
  // ------------------------------------------------

  async checkout(driver) {
    console.log("Testing full checkout...");

    // Add backpack to cart
    await addBackpack(driver);

    // Begin checkout
    await tap(
      driver,
      "Proceed To Checkout button"
    );

    await visible(driver, "login screen");

    // LOGIN
    await type(
      driver,
      "Username input field",
      "bob@example.com"
    );

    await type(
      driver,
      "Password input field",
      "10203040"
    );

    await tap(driver, "Login button");

    await visible(
      driver,
      "checkout address screen"
    );

    // SHIPPING ADDRESS
    await type(
      driver,
      "Full Name* input field",
      "Rebecca Winter"
    );

    await type(
      driver,
      "Address Line 1* input field",
      "Mandorley 112"
    );

    await type(
      driver,
      "City* input field",
      "Truro"
    );

    await type(
      driver,
      "Zip Code* input field",
      "89750"
    );

    await type(
      driver,
      "Country* input field",
      "United Kingdom"
    );

    await tap(driver, "To Payment button");

    await visible(
      driver,
      "checkout payment screen"
    );

    // PAYMENT DETAILS
    await type(
      driver,
      "Full Name* input field",
      "Rebecca Winter"
    );

    await type(
      driver,
      "Card Number* input field",
      "5555555555554444"
    );

    await type(
      driver,
      "Expiration Date* input field",
      "1230"
    );

    await type(
      driver,
      "Security Code* input field",
      "123"
    );

    // REVIEW ORDER
    await tap(driver, "Review Order button");

    await visible(
      driver,
      "checkout review order screen"
    );

    // PLACE ORDER
    await tap(driver, "Place Order button");

    // VERIFY ORDER CONFIRMATION
    await visible(
      driver,
      "checkout complete screen"
    );

    await visible(
      driver,
      "Continue Shopping button"
    );

    console.log("Checkout test passed!");
  }
};

// ==================================================
// 4. SAUCE LABS TEST EXECUTION
// ==================================================

async function execute(name) {
  let driver;
  let passed = false;

  try {
    console.log(
      `Starting ${name} on Sauce Labs...`
    );

    // Create remote Sauce Labs session
    driver = await remote(
      config(`RN Demo: ${name}`)
    );

    console.log(
      "Session ID:",
      driver.sessionId
    );

    // Execute selected test
    await tests[name](driver);

    passed = true;

    console.log(`PASS: ${name}`);

  } catch (error) {
    console.error(
      `FAIL: ${name}: ${error.message}`
    );

    process.exitCode = 1;

    // Save screenshot if test fails
    if (driver) {
      try {
        const imageName = `failed-${name}.png`;

        await driver.saveScreenshot(imageName);

        console.log(
          `Screenshot saved: ${imageName}`
        );

      } catch (screenshotError) {
        console.error(
          "Unable to save failure screenshot:",
          screenshotError.message
        );
      }
    }

  } finally {
    if (driver) {
      try {
        // Report job result to Sauce Labs
        await driver.execute(
          `sauce:job-result=${
            passed ? "passed" : "failed"
          }`
        );

      } catch (error) {
        console.error(
          "Could not update Sauce job result:",
          error.message
        );

        process.exitCode = 1;

      } finally {
        // Always close Appium session
        await driver.deleteSession().catch(error => {
          console.error(
            "Could not close Sauce session:",
            error.message
          );

          process.exitCode = 1;
        });
      }
    }
  }
}

// ==================================================
// 5. MAIN PROGRAM
// ==================================================

(async () => {

  // Verify Sauce Labs credentials exist
  if (
    !process.env.SAUCE_USERNAME ||
    !process.env.SAUCE_ACCESS_KEY
  ) {
    throw new Error(
      "Set SAUCE_USERNAME and SAUCE_ACCESS_KEY before running"
    );
  }

  // Select one test or all tests
  const names =
    RUN === "all"
      ? Object.keys(tests)
      : [RUN];

  for (const name of names) {

    // Validate test name
    if (!tests[name]) {
      throw new Error(
        `Unknown test '${name}'. Use: ${
          Object.keys(tests).join(", ")
        } or all`
      );
    }

    // Run test
    await execute(name);
  }

})().catch(error => {
  console.error(error.message);
  process.exitCode = 1;
});
