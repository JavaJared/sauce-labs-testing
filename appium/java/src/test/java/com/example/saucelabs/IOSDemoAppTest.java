
package com.example.saucelabs;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.ios.IOSDriver;
import io.appium.java_client.ios.options.XCUITestOptions;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;

import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(IOSDemoAppTest.ResultListener.class)
public class IOSDemoAppTest {

    // ==========================================
    // 1. SAUCE LABS CONFIGURATION
    // ==========================================

    private static final String SAUCE_URL =
        "https://ondemand.us-west-1.saucelabs.com/wd/hub";

    private static final String DEFAULT_APP =
        "storage:filename=" +
        "iOS-Real-Device-MyRNDemoApp.ipa";

    private static final Duration TIMEOUT =
        Duration.ofSeconds(25);

    private IOSDriver driver;

    private boolean testPassed = false;

    // ==========================================
    // 2. JUNIT RESULT LISTENER
    // ==========================================

    /**
     * Capture the JUnit test method outcome before
     * @AfterEach reports the result to Sauce Labs.
     */
    public static class ResultListener
        implements AfterTestExecutionCallback {

        @Override
        public void afterTestExecution(
            ExtensionContext context
        ) {

            IOSDemoAppTest test =
                (IOSDemoAppTest)
                context.getRequiredTestInstance();

            test.testPassed =
                context.getExecutionException().isEmpty();
        }
    }

    // ==========================================
    // 3. CREATE SAUCE LABS SESSION
    // ==========================================

    @BeforeEach
    public void setUp(TestInfo testInfo)
        throws Exception {

        String username =
            System.getenv("SAUCE_USERNAME");

        String accessKey =
            System.getenv("SAUCE_ACCESS_KEY");

        if (
            username == null ||
            username.isBlank() ||
            accessKey == null ||
            accessKey.isBlank()
        ) {
            throw new IllegalStateException(
                "Set SAUCE_USERNAME and " +
                "SAUCE_ACCESS_KEY before running."
            );
        }

        String testName =
            testInfo.getTestMethod()
                .orElseThrow()
                .getName();

        String app =
            environmentOrDefault(
                "SAUCE_IOS_APP",
                DEFAULT_APP
            );

        String device =
            environmentOrDefault(
                "SAUCE_IOS_DEVICE",
                "iPhone.*"
            );

        XCUITestOptions options =
            new XCUITestOptions();

        options.setPlatformName("iOS");
        options.setAutomationName("XCUITest");
        options.setDeviceName(device);
        options.setApp(app);

        String iosVersion =
            System.getenv("SAUCE_IOS_VERSION");

        if (
            iosVersion != null &&
            !iosVersion.isBlank()
        ) {
            options.setPlatformVersion(iosVersion);
        }

        Map<String, Object> sauceOptions =
            new HashMap<>();

        sauceOptions.put("username", username);
        sauceOptions.put("accessKey", accessKey);
        sauceOptions.put("appiumVersion", "latest");
        sauceOptions.put(
            "build",
            "RN-Demo-iOS-Java"
        );
        sauceOptions.put("name", testName);

        options.setCapability(
            "sauce:options",
            sauceOptions
        );

        driver = new IOSDriver(
            new URL(SAUCE_URL),
            options
        );

        System.out.println(
            "\nSauce Labs session: " +
            driver.getSessionId()
        );

        System.out.println(
            "Running test: " + testName
        );
    }

    private static String environmentOrDefault(
        String name,
        String defaultValue
    ) {

        String value = System.getenv(name);

        return value == null || value.isBlank()
            ? defaultValue
            : value;
    }

    // ==========================================
    // 4. REPORT RESULT + RELEASE DEVICE
    // ==========================================

    @AfterEach
    public void tearDown() {

        if (driver == null) {
            return;
        }

        String status =
            testPassed ? "passed" : "failed";

        System.out.println(
            "\nJUnit result: " + status
        );

        System.out.println(
            "Reporting to Sauce Labs: " +
            status
        );

        System.out.println(
            "Sauce Labs session: " +
            driver.getSessionId()
        );

        if (!testPassed) {
            saveDebugArtifacts("ios-java-failure");
        }

        try {
            driver.executeScript(
                "sauce:job-result=" + status
            );

            System.out.println(
                "Sauce Labs job marked as " +
                status
            );

        } finally {
            driver.quit();
        }
    }

    // ==========================================
    // 5. FAILURE DIAGNOSTICS
    // ==========================================

    /**
     * Save both a screenshot and the current
     * accessibility hierarchy.
     *
     * The XML can help diagnose iOS selectors
     * that are visible to users but not exposed
     * as expected to Appium.
     */
    private void saveDebugArtifacts(String prefix) {

        try {
            Path directory =
                Paths.get("screenshots");

            Files.createDirectories(directory);

            String timestamp =
                String.valueOf(
                    System.currentTimeMillis()
                );

            File screenshot =
                driver.getScreenshotAs(
                    OutputType.FILE
                );

            Path imagePath =
                directory.resolve(
                    prefix + "-" +
                    timestamp + ".png"
                );

            Files.copy(
                screenshot.toPath(),
                imagePath,
                StandardCopyOption.REPLACE_EXISTING
            );

            System.out.println(
                "Screenshot saved: " + imagePath
            );

            Path xmlPath =
                directory.resolve(
                    prefix + "-" +
                    timestamp + ".xml"
                );

            Files.writeString(
                xmlPath,
                driver.getPageSource(),
                StandardCharsets.UTF_8
            );

            System.out.println(
                "Page source saved: " + xmlPath
            );

        } catch (Exception error) {
            System.out.println(
                "Unable to save diagnostics: " +
                error.getMessage()
            );
        }
    }

    // ==========================================
    // 6. ELEMENT HELPERS
    // ==========================================

    private WebElement element(String id) {

        return new WebDriverWait(
            driver,
            TIMEOUT
        ).until(
            ExpectedConditions
                .visibilityOfElementLocated(
                    AppiumBy.accessibilityId(id)
                )
        );
    }

    private void tap(String id) {

        element(id).click();
    }

    private void swipeUp() {

        driver.executeScript(
            "mobile: swipe",
            Map.of(
                "direction",
                "up"
            )
        );
    }

    private WebElement findWithScroll(
        String accessibilityId
    ) {

        for (
            int attempt = 0;
            attempt <= 5;
            attempt++
        ) {

            List<WebElement> matches =
                driver.findElements(
                    AppiumBy.accessibilityId(
                        accessibilityId
                    )
                );

            for (WebElement match : matches) {
                try {
                    if (match.isDisplayed()) {
                        return match;
                    }
                } catch (Exception ignored) {
                    // Try another matching element.
                }
            }

            if (attempt < 5) {
                swipeUp();
            }
        }

        throw new AssertionError(
            "Could not find visible element: " +
            accessibilityId
        );
    }

    private void tapWithScroll(String id) {

        findWithScroll(id).click();
    }

    // ==========================================
    // 7. IOS KEYBOARD HELPERS
    // ==========================================

    /**
     * Attempt keyboard dismissal without failing
     * the current interaction immediately.
     *
     * iOS numeric keyboards sometimes have no
     * Done or Return key.
     */
    private void dismissKeyboard() {

        try {
            if (!driver.isKeyboardShown()) {
                return;
            }
        } catch (Exception ignored) {
            // Keyboard state not available.
        }

        try {
            driver.hideKeyboard();

            if (!driver.isKeyboardShown()) {
                return;
            }

        } catch (Exception ignored) {
            // Try an explicit keyboard button.
        }

        for (String label :
            new String[]{"Done", "Return"}) {

            try {
                List<WebElement> buttons =
                    driver.findElements(
                        AppiumBy.accessibilityId(label)
                    );

                for (WebElement button : buttons) {
                    if (button.isDisplayed()) {
                        button.click();

                        if (!driver.isKeyboardShown()) {
                            return;
                        }
                    }
                }

            } catch (Exception ignored) {
                // Try the next option.
            }
        }

        // Tapping a clear area above the keyboard
        // can dismiss it on some iOS devices.
        try {
            Dimension size =
                driver.manage()
                    .window()
                    .getSize();

            driver.executeScript(
                "mobile: tap",
                Map.of(
                    "x",
                    (int) (
                        size.getWidth() * 0.5
                    ),
                    "y",
                    (int) (
                        size.getHeight() * 0.15
                    )
                )
            );

        } catch (Exception error) {
            System.out.println(
                "Keyboard dismissal unavailable: " +
                error.getMessage()
            );
        }
    }

    private void typeText(
        String accessibilityId,
        String value
    ) {

        WebElement input =
            findWithScroll(accessibilityId);

        input.clear();
        input.sendKeys(value);

        // Best-effort dismissal; navigation
        // buttons are checked separately.
        dismissKeyboard();
    }

    // ==========================================
    // 8. PRODUCT + CART HELPERS
    // ==========================================

    private void openFirstProduct() {

        element("products screen");

        List<WebElement> products =
            driver.findElements(
                AppiumBy.accessibilityId(
                    "store item"
                )
            );

        assertFalse(
            products.isEmpty(),
            "Product catalog should not be empty"
        );

        products.get(0).click();

        element("product screen");
    }

    private void addBackpackToCart() {

        openFirstProduct();

        assertEquals(
            "$29.99",
            element(
                "product price"
            ).getText().trim()
        );

        tapWithScroll("Add To Cart button");

        // Use the Cart tab on iOS.
        tap("tab bar option cart");

        element("cart screen");

        assertEquals(
            "Sauce Labs Backpack",
            element(
                "product label"
            ).getText().trim()
        );
    }

    private void openLoginFromCart() {

        addBackpackToCart();

        tapWithScroll(
            "Proceed To Checkout button"
        );

        element("login screen");
    }

    // ==========================================
    // 9. IOS CART QUANTITY HELPER
    // ==========================================

    private int getCartQuantity() {

        WebElement counter =
            element("counter amount");

        // Check the parent and any child text nodes.
        List<WebElement> candidates =
            new java.util.ArrayList<>();

        candidates.add(counter);

        candidates.addAll(
            counter.findElements(
                By.className(
                    "XCUIElementTypeStaticText"
                )
            )
        );

        for (WebElement candidate : candidates) {

            for (String attribute :
                new String[]{
                    "value",
                    "label",
                    "name"
                }) {

                try {
                    String value =
                        candidate.getAttribute(
                            attribute
                        );

                    if (
                        value != null &&
                        value.trim().matches("\\d+")
                    ) {
                        return Integer.parseInt(
                            value.trim()
                        );
                    }

                } catch (Exception ignored) {
                    // Try another attribute.
                }
            }

            try {
                String text =
                    candidate.getText().trim();

                if (text.matches("\\d+")) {
                    return Integer.parseInt(text);
                }

            } catch (Exception ignored) {
                // Try another element.
            }
        }

        throw new AssertionError(
            "Could not read iOS cart quantity. " +
            "Inspect the counter in Appium Inspector."
        );
    }

    // ==========================================
    // 10. IOS REVIEW ORDER HELPER
    // ==========================================

    /**
     * The demo app's Review Order button is in a
     * fixed footer outside the scrollable payment
     * form.
     *
     * Avoid repeatedly swiping the payment form.
     * Instead, dismiss the keyboard, locate the
     * footer button, and verify navigation.
     */
    private void tapReviewOrder() {

        System.out.println(
            "Preparing to select Review Order..."
        );

        dismissKeyboard();

        try {
            WebElement reviewButton =
                new WebDriverWait(
                    driver,
                    TIMEOUT
                ).until(
                    ExpectedConditions
                        .elementToBeClickable(
                            AppiumBy.accessibilityId(
                                "Review Order button"
                            )
                        )
                );

            System.out.println(
                "Review Order button found."
            );

            reviewButton.click();

            element(
                "checkout review order screen"
            );

            System.out.println(
                "Reached order review screen."
            );

        } catch (Exception error) {

            System.out.println(
                "Review Order interaction failed: " +
                error.getMessage()
            );

            saveDebugArtifacts(
                "ios-payment-debug"
            );

            throw new AssertionError(
                "Could not select Review Order " +
                "or reach the review screen. " +
                "Inspect the saved screenshot " +
                "and page-source XML.",
                error
            );
        }
    }

    // ==========================================
    // TEST 1: PRODUCT CATALOG
    // ==========================================

    @Test
    public void testProductCatalog() {

        element("products screen");

        List<WebElement> products =
            driver.findElements(
                AppiumBy.accessibilityId(
                    "store item"
                )
            );

        assertFalse(
            products.isEmpty(),
            "Product catalog should not be empty"
        );

        List<WebElement> names =
            driver.findElements(
                AppiumBy.accessibilityId(
                    "store item text"
                )
            );

        boolean foundBackpack =
            names.stream().anyMatch(
                item -> item.getText().contains(
                    "Sauce Labs Backpack"
                )
            );

        assertTrue(
            foundBackpack,
            "Backpack should be in the catalog"
        );

        System.out.println(
            "PASS: iOS product catalog verified"
        );
    }

    // ==========================================
    // TEST 2: PRODUCT DETAILS
    // ==========================================

    @Test
    public void testProductDetails() {

        openFirstProduct();

        assertEquals(
            "$29.99",
            element(
                "product price"
            ).getText().trim()
        );

        WebElement description =
            findWithScroll(
                "product description"
            );

        assertTrue(
            description.getText()
                .toLowerCase()
                .contains("carry.allthethings"),
            "Expected backpack description"
        );

        assertTrue(
            findWithScroll(
                "Add To Cart button"
            ).isDisplayed()
        );

        System.out.println(
            "PASS: iOS product details verified"
        );
    }

    // ==========================================
    // TEST 3: SHOPPING CART
    // ==========================================

    @Test
    public void testShoppingCart() {

        addBackpackToCart();

        assertEquals(
            1,
            getCartQuantity(),
            "Initial quantity should be 1"
        );

        tap("counter plus button");

        new WebDriverWait(
            driver,
            TIMEOUT
        ).until(
            d -> getCartQuantity() == 2
        );

        assertEquals(
            2,
            getCartQuantity(),
            "Updated quantity should be 2"
        );

        tap("remove item");

        assertTrue(
            element(
                "Go Shopping button"
            ).isDisplayed()
        );

        System.out.println(
            "PASS: iOS shopping cart verified"
        );
    }

    // ==========================================
    // TEST 4: LOCKED-OUT LOGIN
    // ==========================================

    @Test
    public void testLockedOutLogin() {

        openLoginFromCart();

        typeText(
            "Username input field",
            "alice@example.com"
        );

        typeText(
            "Password input field",
            "10203040"
        );

        tapWithScroll("Login button");

        WebElement error =
            new WebDriverWait(
                driver,
                TIMEOUT
            ).until(
                ExpectedConditions
                    .visibilityOfElementLocated(
                        AppiumBy.iOSNsPredicateString(
                            "label == " +
                            "'Sorry, this user has been locked out.'"
                        )
                    )
            );

        assertTrue(error.isDisplayed());

        System.out.println(
            "PASS: iOS locked-out login verified"
        );
    }

    // ==========================================
    // TEST 5: EMPTY LOGIN VALIDATION
    // ==========================================

    @Test
    public void testEmptyLoginValidation() {

        openLoginFromCart();

        tapWithScroll("Login button");

        WebElement error =
            new WebDriverWait(
                driver,
                TIMEOUT
            ).until(
                ExpectedConditions
                    .visibilityOfElementLocated(
                        AppiumBy.iOSNsPredicateString(
                            "label == 'Username is required'"
                        )
                    )
            );

        assertTrue(error.isDisplayed());

        System.out.println(
            "PASS: iOS empty login validation verified"
        );
    }

    // ==========================================
    // TEST 6: COMPLETE CHECKOUT
    // ==========================================

    @Test
    public void testCompleteCheckout() {

        System.out.println(
            "Starting complete iOS checkout..."
        );

        // --------------------------------------
        // STEP 1: ADD PRODUCT + OPEN LOGIN
        // --------------------------------------

        openLoginFromCart();

        // --------------------------------------
        // STEP 2: LOGIN
        // --------------------------------------

        typeText(
            "Username input field",
            "bob@example.com"
        );

        typeText(
            "Password input field",
            "10203040"
        );

        tapWithScroll("Login button");

        element("checkout address screen");

        System.out.println(
            "Login successful."
        );

        // --------------------------------------
        // STEP 3: SHIPPING ADDRESS
        // --------------------------------------

        typeText(
            "Full Name* input field",
            "Rebecca Winter"
        );

        typeText(
            "Address Line 1* input field",
            "Mandorley 112"
        );

        typeText(
            "City* input field",
            "Truro"
        );

        typeText(
            "Zip Code* input field",
            "89750"
        );

        typeText(
            "Country* input field",
            "United Kingdom"
        );

        dismissKeyboard();

        tapWithScroll(
            "To Payment button"
        );

        element(
            "checkout payment screen"
        );

        System.out.println(
            "Shipping address accepted."
        );

        // --------------------------------------
        // STEP 4: PAYMENT DETAILS
        // --------------------------------------

        typeText(
            "Full Name* input field",
            "Rebecca Winter"
        );

        typeText(
            "Card Number* input field",
            "5555555555554444"
        );

        typeText(
            "Expiration Date* input field",
            "1230"
        );

        typeText(
            "Security Code* input field",
            "123"
        );

        // --------------------------------------
        // STEP 5: REVIEW ORDER
        // --------------------------------------

        tapReviewOrder();

        // --------------------------------------
        // STEP 6: PLACE ORDER
        // --------------------------------------

        tapWithScroll(
            "Place Order button"
        );

        // --------------------------------------
        // STEP 7: CONFIRM ORDER
        // --------------------------------------

        WebElement confirmation =
            element(
                "checkout complete screen"
            );

        assertTrue(
            confirmation.isDisplayed()
        );

        assertTrue(
            element(
                "Continue Shopping button"
            ).isDisplayed()
        );

        System.out.println(
            "PASS: Complete iOS checkout verified"
        );
    }
}
