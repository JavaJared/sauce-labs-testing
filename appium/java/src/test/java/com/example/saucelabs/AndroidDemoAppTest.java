
package com.example.saucelabs;

import io.appium.java_client.AppiumBy;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(AndroidDemoAppTest.ResultListener.class)
public class AndroidDemoAppTest {

    // ==========================================
    // 1. SAUCE LABS CONFIGURATION
    // ==========================================

    private static final String SAUCE_URL =
        "https://ondemand.us-west-1.saucelabs.com/wd/hub";

    private static final String APP =
        "storage:filename=" +
        "Android-MyDemoAppRN.1.3.0.build-244.apk";

    private static final Duration TIMEOUT =
        Duration.ofSeconds(25);

    private AndroidDriver driver;

    private boolean testPassed = false;

    // ==========================================
    // 2. JUNIT RESULT LISTENER
    // ==========================================

    /**
     * Capture the outcome of the test method.
     *
     * JUnit calls this after the test method
     * executes and before @AfterEach.
     *
     * That ensures Sauce Labs receives the
     * correct result while the Appium session
     * is still running.
     */
    public static class ResultListener
        implements AfterTestExecutionCallback {

        @Override
        public void afterTestExecution(
            ExtensionContext context
        ) {

            AndroidDemoAppTest test =
                (AndroidDemoAppTest)
                context.getRequiredTestInstance();

            test.testPassed =
                context.getExecutionException().isEmpty();
        }
    }

    // ==========================================
    // 3. START SAUCE LABS SESSION
    // ==========================================

    @BeforeEach
    public void setUp(TestInfo testInfo)
        throws Exception {

        String username =
            System.getenv("SAUCE_USERNAME");

        String accessKey =
            System.getenv("SAUCE_ACCESS_KEY");

        if (username == null ||
            username.isBlank() ||
            accessKey == null ||
            accessKey.isBlank()) {

            throw new IllegalStateException(
                "Set SAUCE_USERNAME and " +
                "SAUCE_ACCESS_KEY before running."
            );
        }

        String testName =
            testInfo.getTestMethod()
                .orElseThrow()
                .getName();

        UiAutomator2Options options =
            new UiAutomator2Options();

        options.setPlatformName("Android");
        options.setAutomationName("UiAutomator2");
        options.setDeviceName("Google.*");
        options.setApp(APP);

        Map<String, Object> sauceOptions =
            new HashMap<>();

        sauceOptions.put("username", username);
        sauceOptions.put("accessKey", accessKey);
        sauceOptions.put(
            "appiumVersion",
            "latest"
        );
        sauceOptions.put(
            "build",
            "RN-Demo-Android-Java"
        );
        sauceOptions.put(
            "name",
            testName
        );

        options.setCapability(
            "sauce:options",
            sauceOptions
        );

        driver = new AndroidDriver(
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

    // ==========================================
    // 4. REPORT RESULT + CLOSE SESSION
    // ==========================================

    @AfterEach
    public void tearDown() {

        if (driver == null) {
            return;
        }

        String status =
            testPassed ? "passed" : "failed";

        System.out.println(
            "JUnit result: " + status
        );

        System.out.println(
            "Reporting to Sauce Labs: " +
            status
        );

        if (!testPassed) {
            saveFailureScreenshot();
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
    // 5. FAILURE SCREENSHOTS
    // ==========================================

    private void saveFailureScreenshot() {

        try {
            Path directory =
                Paths.get("screenshots");

            Files.createDirectories(directory);

            File screenshot =
                driver.getScreenshotAs(
                    OutputType.FILE
                );

            Path destination =
                directory.resolve(
                    "android-java-failure-" +
                    System.currentTimeMillis() +
                    ".png"
                );

            Files.copy(
                screenshot.toPath(),
                destination
            );

            System.out.println(
                "Screenshot saved: " +
                destination
            );

        } catch (Exception error) {
            System.out.println(
                "Could not save screenshot: " +
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
            ExpectedConditions.visibilityOfElementLocated(
                AppiumBy.accessibilityId(id)
            )
        );
    }

    private void tap(String id) {

        element(id).click();
    }

    private void swipeUp() {

        Dimension size =
            driver.manage()
                .window()
                .getSize();

        int width = size.getWidth();
        int height = size.getHeight();

        Map<String, Object> gesture =
            new HashMap<>();

        gesture.put(
            "left",
            (int) (width * 0.10)
        );

        gesture.put(
            "top",
            (int) (height * 0.20)
        );

        gesture.put(
            "width",
            (int) (width * 0.80)
        );

        gesture.put(
            "height",
            (int) (height * 0.60)
        );

        gesture.put(
            "direction",
            "up"
        );

        gesture.put(
            "percent",
            0.75
        );

        driver.executeScript(
            "mobile: swipeGesture",
            gesture
        );
    }

    private WebElement findWithScroll(
        String accessibilityId
    ) {

        for (int attempt = 0;
             attempt <= 5;
             attempt++) {

            List<WebElement> matches =
                driver.findElements(
                    AppiumBy.accessibilityId(
                        accessibilityId
                    )
                );

            for (WebElement match : matches) {
                if (match.isDisplayed()) {
                    return match;
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

    private void typeText(
        String accessibilityId,
        String value
    ) {

        WebElement input =
            findWithScroll(accessibilityId);

        input.clear();
        input.sendKeys(value);

        try {
            driver.hideKeyboard();
        } catch (Exception ignored) {
            // Keyboard may already be hidden.
        }
    }

    // ==========================================
    // 7. SHOPPING HELPERS
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
            "Catalog should contain products"
        );

        products.get(0).click();

        element("product screen");
    }

    private void addBackpackToCart() {

        openFirstProduct();

        WebElement price =
            element("product price");

        assertEquals(
            "$29.99",
            price.getText().trim()
        );

        tapWithScroll(
            "Add To Cart button"
        );

        // Wait until the cart badge is visible
        element("cart badge");

        tap("cart badge");

        element("cart screen");

        WebElement product =
            element("product label");

        assertEquals(
            "Sauce Labs Backpack",
            product.getText().trim()
        );
    }

    private void openLoginFromCart() {

        addBackpackToCart();

        tapWithScroll(
            "Proceed To Checkout button"
        );

        element("login screen");
    }

    private int getCartQuantity() {

        WebElement counter =
            element("counter amount");

        // React Native may expose the value
        // in a descendant Android TextView.
        List<WebElement> textViews =
            counter.findElements(
                By.className(
                    "android.widget.TextView"
                )
            );

        for (WebElement textView : textViews) {

            String text =
                textView.getText().trim();

            if (text.matches("\\d+")) {
                return Integer.parseInt(text);
            }
        }

        String parentText =
            counter.getText().trim();

        if (parentText.matches("\\d+")) {
            return Integer.parseInt(
                parentText
            );
        }

        throw new AssertionError(
            "Unable to read cart quantity. " +
            "Inspect the counter in Appium Inspector."
        );
    }

    // ==========================================
    // 8. TEST 1: PRODUCT CATALOG
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

        boolean foundBackpack = names.stream()
            .anyMatch(
                item -> item.getText().contains(
                    "Sauce Labs Backpack"
                )
            );

        assertTrue(
            foundBackpack,
            "Backpack should be in catalog"
        );

        System.out.println(
            "PASS: Product catalog verified"
        );
    }

    // ==========================================
    // 9. TEST 2: PRODUCT DETAILS
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
                .contains(
                    "carry.allthethings"
                ),
            "Expected backpack description"
        );

        WebElement addButton =
            findWithScroll(
                "Add To Cart button"
            );

        assertTrue(
            addButton.isDisplayed()
        );

        System.out.println(
            "PASS: Product details verified"
        );
    }

    // ==========================================
    // 10. TEST 3: SHOPPING CART
    // ==========================================

    @Test
    public void testShoppingCart() {

        addBackpackToCart();

        int initialQuantity =
            getCartQuantity();

        assertEquals(
            1,
            initialQuantity,
            "Initial cart quantity should be 1"
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

        WebElement emptyCart =
            element(
                "Go Shopping button"
            );

        assertTrue(
            emptyCart.isDisplayed()
        );

        System.out.println(
            "PASS: Shopping cart verified"
        );
    }

    // ==========================================
    // 11. TEST 4: LOCKED-OUT LOGIN
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

        tap("Login button");

        WebElement error =
            new WebDriverWait(
                driver,
                TIMEOUT
            ).until(
                ExpectedConditions
                    .visibilityOfElementLocated(
                        By.xpath(
                            "//*[@text=" +
                            "'Sorry, this user has been locked out.']"
                        )
                    )
            );

        assertTrue(
            error.isDisplayed()
        );

        System.out.println(
            "PASS: Locked-out login verified"
        );
    }

    // ==========================================
    // 12. TEST 5: EMPTY LOGIN VALIDATION
    // ==========================================

    @Test
    public void testEmptyLoginValidation() {

        openLoginFromCart();

        tap("Login button");

        WebElement error =
            new WebDriverWait(
                driver,
                TIMEOUT
            ).until(
                ExpectedConditions
                    .visibilityOfElementLocated(
                        By.xpath(
                            "//*[@text='Username is required']"
                        )
                    )
            );

        assertTrue(
            error.isDisplayed()
        );

        System.out.println(
            "PASS: Empty login validation verified"
        );
    }

    // ==========================================
    // 13. TEST 6: COMPLETE CHECKOUT
    // ==========================================

    @Test
    public void testCompleteCheckout() {

        openLoginFromCart();

        // LOGIN
        typeText(
            "Username input field",
            "bob@example.com"
        );

        typeText(
            "Password input field",
            "10203040"
        );

        tap("Login button");

        element("checkout address screen");

        // SHIPPING ADDRESS
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

        tapWithScroll(
            "To Payment button"
        );

        element("checkout payment screen");

        // PAYMENT DETAILS
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

        tapWithScroll(
            "Review Order button"
        );

        element(
            "checkout review order screen"
        );

        // PLACE ORDER
        tapWithScroll(
            "Place Order button"
        );

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
            "PASS: Complete checkout verified"
        );
    }
}
