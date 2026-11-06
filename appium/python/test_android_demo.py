
"""
Sauce Labs My Demo App - Android Tests

Framework: Python + pytest + Appium
Platform: Android Real Devices
Cloud Provider: Sauce Labs

Application:
Android-MyDemoAppRN.1.3.0.build-244.apk

Run all tests:
    python -m pytest -v -s test_android_demo.py

Run one test:
    python -m pytest -v -s test_android_demo.py::test_product_catalog
"""

import os
import re
import time
from pathlib import Path

import pytest

from appium import webdriver
from appium.options.android import UiAutomator2Options
from appium.webdriver.common.appiumby import AppiumBy

from selenium.webdriver.support.ui import WebDriverWait


# ==================================================
# 1. SAUCE LABS CONFIGURATION
# ==================================================

SAUCE_URL = (
    "https://ondemand.us-west-1.saucelabs.com/wd/hub"
)

APP = (
    "storage:filename="
    "Android-MyDemoAppRN.1.3.0.build-244.apk"
)

TIMEOUT = 20


def create_driver(test_name):
    """Create a remote Appium session on Sauce Labs."""

    username = os.getenv("SAUCE_USERNAME")
    access_key = os.getenv("SAUCE_ACCESS_KEY")

    if not username or not access_key:
        raise RuntimeError(
            "SAUCE_USERNAME and SAUCE_ACCESS_KEY must be set."
        )

    capabilities = {
        "platformName": "Android",

        "appium:automationName": "UiAutomator2",
        "appium:deviceName": "Google.*",
        "appium:app": APP,

        "sauce:options": {
            "username": username,
            "accessKey": access_key,
            "appiumVersion": "latest",
            "build": "RN-Demo-Android-Python",
            "name": test_name,
        },
    }

    options = UiAutomator2Options()
    options.load_capabilities(capabilities)

    driver = webdriver.Remote(
        command_executor=SAUCE_URL,
        options=options,
    )

    print(
        f"\nSauce Labs session: {driver.session_id}"
    )

    return driver


# ==================================================
# 2. PYTEST FIXTURE
# ==================================================

@pytest.fixture
def driver(request):
    """
    Create a Sauce Labs session for each test.

    The pytest_runtest_makereport hook in conftest.py
    saves the assertion result as request.node.rep_call.
    """

    session = create_driver(request.node.name)

    # Run the test
    yield session

    # Read the result captured by conftest.py
    report = getattr(
        request.node,
        "rep_call",
        None,
    )

    passed = (
        report is not None
        and report.passed
    )

    status = "passed" if passed else "failed"

    print(
        f"\nPytest result: "
        f"{report.outcome if report else 'unknown'}"
    )

    print(
        f"Reporting to Sauce Labs: {status}"
    )

    print(
        f"Sauce Labs session: {session.session_id}"
    )

    # Capture a screenshot if the test failed
    if not passed:
        Path("screenshots").mkdir(
            exist_ok=True
        )

        screenshot = (
            f"screenshots/{request.node.name}.png"
        )

        try:
            session.save_screenshot(screenshot)

            print(
                f"Screenshot saved: {screenshot}"
            )

        except Exception as error:
            print(
                f"Unable to save screenshot: {error}"
            )

    # Report the result and release the device
    try:
        session.execute_script(
            f"sauce:job-result={status}"
        )

        print(
            f"Sauce Labs job marked as {status}"
        )

    finally:
        session.quit()


# ==================================================
# 3. HELPER FUNCTIONS
# ==================================================

def element(driver, accessibility_id):
    """Wait for an element to become visible."""

    def find_visible(d):
        matches = d.find_elements(
            AppiumBy.ACCESSIBILITY_ID,
            accessibility_id,
        )

        for match in matches:
            if match.is_displayed():
                return match

        return False

    return WebDriverWait(
        driver,
        TIMEOUT,
    ).until(find_visible)


def tap(driver, accessibility_id):
    """Tap an element by accessibility ID."""

    target = element(
        driver,
        accessibility_id,
    )

    target.click()


def swipe_up(driver):
    """
    Scroll upward using the UiAutomator2
    mobile swipeGesture command.
    """

    size = driver.get_window_size()

    width = size["width"]
    height = size["height"]

    driver.execute_script(
        "mobile: swipeGesture",
        {
            "left": int(width * 0.10),
            "top": int(height * 0.20),
            "width": int(width * 0.80),
            "height": int(height * 0.60),
            "direction": "up",
            "percent": 0.75,
        },
    )


def find_with_scroll(
    driver,
    accessibility_id,
    max_swipes=5,
):
    """Find a visible element, scrolling if necessary."""

    for attempt in range(max_swipes + 1):

        matches = driver.find_elements(
            AppiumBy.ACCESSIBILITY_ID,
            accessibility_id,
        )

        for match in matches:
            if match.is_displayed():
                return match

        if attempt < max_swipes:
            swipe_up(driver)

    raise AssertionError(
        f"Could not find visible element: "
        f"{accessibility_id}"
    )


def type_text(
    driver,
    accessibility_id,
    value,
):
    """Enter text and dismiss the Android keyboard."""

    field = find_with_scroll(
        driver,
        accessibility_id,
    )

    field.clear()
    field.send_keys(value)

    try:
        driver.hide_keyboard()
    except Exception:
        pass


def open_first_product(driver):
    """Open the first product from the catalog."""

    element(
        driver,
        "products screen",
    )

    products = driver.find_elements(
        AppiumBy.ACCESSIBILITY_ID,
        "store item",
    )

    assert products, (
        "Expected products in the catalog"
    )

    products[0].click()

    element(
        driver,
        "product screen",
    )


def add_backpack_to_cart(driver):
    """Add the Sauce Labs Backpack to the cart."""

    open_first_product(driver)

    price = element(
        driver,
        "product price",
    )

    assert price.text.strip() == "$29.99"

    add_button = find_with_scroll(
        driver,
        "Add To Cart button",
    )

    add_button.click()

    # Wait for the cart badge animation
    time.sleep(0.8)

    tap(
        driver,
        "cart badge",
    )

    element(
        driver,
        "cart screen",
    )

    product = element(
        driver,
        "product label",
    )

    assert product.text.strip() == (
        "Sauce Labs Backpack"
    )


def open_login_from_cart(driver):
    """Navigate to the login screen through checkout."""

    add_backpack_to_cart(driver)

    tap(
        driver,
        "Proceed To Checkout button",
    )

    element(
        driver,
        "login screen",
    )


def get_cart_quantity(driver):
    """
    Read the numeric quantity from the cart counter.

    React Native places the accessibility label
    on a container, so the container's .text
    may be empty on Android.

    Look for a descendant TextView first.
    """

    counter = element(
        driver,
        "counter amount",
    )

    text_views = counter.find_elements(
        AppiumBy.CLASS_NAME,
        "android.widget.TextView",
    )

    for text_view in text_views:
        value = text_view.text.strip()

        if value.isdigit():
            return int(value)

    # Fallback if Android exposes text on the parent
    value = counter.text.strip()

    if value.isdigit():
        return int(value)

    raise AssertionError(
        "Could not read cart quantity. "
        "Inspect the counter amount element "
        "in Appium Inspector."
    )


# ==================================================
# 4. TEST CASES
# ==================================================

# --------------------------------------------------
# TEST 1: PRODUCT CATALOG
# --------------------------------------------------

def test_product_catalog(driver):

    element(
        driver,
        "products screen",
    )

    products = driver.find_elements(
        AppiumBy.ACCESSIBILITY_ID,
        "store item",
    )

    assert len(products) > 0, (
        "Product catalog should not be empty"
    )

    names = driver.find_elements(
        AppiumBy.ACCESSIBILITY_ID,
        "store item text",
    )

    product_names = [
        item.text for item in names
    ]

    assert "Sauce Labs Backpack" in product_names

    print("PASS: Product catalog verified")


# --------------------------------------------------
# TEST 2: PRODUCT DETAILS
# --------------------------------------------------

def test_product_details(driver):

    open_first_product(driver)

    price = element(
        driver,
        "product price",
    )

    assert price.text.strip() == "$29.99"

    description = find_with_scroll(
        driver,
        "product description",
    )

    assert re.search(
        r"carry\.allTheThings",
        description.text,
        re.IGNORECASE,
    )

    add_button = find_with_scroll(
        driver,
        "Add To Cart button",
    )

    assert add_button.is_displayed()

    print("PASS: Product details verified")


# --------------------------------------------------
# TEST 3: SHOPPING CART
# --------------------------------------------------

def test_shopping_cart(driver):

    add_backpack_to_cart(driver)

    # Verify initial quantity
    quantity = get_cart_quantity(driver)

    assert quantity == 1, (
        f"Expected quantity 1, got {quantity}"
    )

    print("Initial cart quantity: 1")

    # Increase quantity to 2
    tap(
        driver,
        "counter plus button",
    )

    # Wait for React Native state to update
    WebDriverWait(
        driver,
        TIMEOUT,
    ).until(
        lambda d: get_cart_quantity(d) == 2
    )

    quantity = get_cart_quantity(driver)

    assert quantity == 2, (
        f"Expected quantity 2, got {quantity}"
    )

    print("Updated cart quantity: 2")

    # Remove product entirely
    tap(
        driver,
        "remove item",
    )

    empty_cart = element(
        driver,
        "Go Shopping button",
    )

    assert empty_cart.is_displayed()

    print("PASS: Shopping cart verified")


# --------------------------------------------------
# TEST 4: LOCKED-OUT USER LOGIN
# --------------------------------------------------

def test_locked_out_login(driver):

    open_login_from_cart(driver)

    type_text(
        driver,
        "Username input field",
        "alice@example.com",
    )

    type_text(
        driver,
        "Password input field",
        "10203040",
    )

    tap(
        driver,
        "Login button",
    )

    error = WebDriverWait(
        driver,
        TIMEOUT,
    ).until(
        lambda d: d.find_element(
            AppiumBy.XPATH,
            '//*[@text="Sorry, this user has been locked out."]',
        )
    )

    assert error.is_displayed()

    print("PASS: Locked-out login verified")


# --------------------------------------------------
# TEST 5: EMPTY LOGIN VALIDATION
# --------------------------------------------------

def test_empty_login_validation(driver):

    open_login_from_cart(driver)

    # Attempt login without credentials
    tap(
        driver,
        "Login button",
    )

    error = WebDriverWait(
        driver,
        TIMEOUT,
    ).until(
        lambda d: d.find_element(
            AppiumBy.XPATH,
            '//*[@text="Username is required"]',
        )
    )

    assert error.is_displayed()

    print("PASS: Empty login validation verified")


# --------------------------------------------------
# TEST 6: COMPLETE CHECKOUT
# --------------------------------------------------

def test_complete_checkout(driver):

    open_login_from_cart(driver)

    # LOGIN
    type_text(
        driver,
        "Username input field",
        "bob@example.com",
    )

    type_text(
        driver,
        "Password input field",
        "10203040",
    )

    tap(
        driver,
        "Login button",
    )

    element(
        driver,
        "checkout address screen",
    )

    # SHIPPING ADDRESS
    shipping_fields = {
        "Full Name* input field": "Rebecca Winter",
        "Address Line 1* input field": "Mandorley 112",
        "City* input field": "Truro",
        "Zip Code* input field": "89750",
        "Country* input field": "United Kingdom",
    }

    for name, value in shipping_fields.items():
        type_text(
            driver,
            name,
            value,
        )

    tap(
        driver,
        "To Payment button",
    )

    element(
        driver,
        "checkout payment screen",
    )

    # PAYMENT DETAILS
    payment_fields = {
        "Full Name* input field": "Rebecca Winter",
        "Card Number* input field": "5555555555554444",
        "Expiration Date* input field": "1230",
        "Security Code* input field": "123",
    }

    for name, value in payment_fields.items():
        type_text(
            driver,
            name,
            value,
        )

    tap(
        driver,
        "Review Order button",
    )

    element(
        driver,
        "checkout review order screen",
    )

    # PLACE ORDER
    tap(
        driver,
        "Place Order button",
    )

    confirmation = element(
        driver,
        "checkout complete screen",
    )

    assert confirmation.is_displayed()

    element(
        driver,
        "Continue Shopping button",
    )

    print("PASS: Complete checkout verified")
