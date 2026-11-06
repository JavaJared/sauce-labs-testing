
"""
Sauce Labs My Demo App - iOS Tests

Framework: Python + pytest + Appium
Platform: iOS Real Devices
Provider: Sauce Labs Real Device Cloud

Application:
iOS-Real-Device-MyRNDemoApp.ipa

Available tests:
1. Product catalog
2. Product details
3. Shopping cart
4. Locked-out login
5. Empty login validation
6. Complete checkout

Run all:
    python -m pytest -v -s test_ios_demo.py

Run checkout only:
    python -m pytest -v -s \
        test_ios_demo.py::test_ios_complete_checkout

Requirements:
- Appium-Python-Client
- pytest
- SAUCE_USERNAME environment variable
- SAUCE_ACCESS_KEY environment variable
- conftest.py containing the pytest result hook
"""

import os
import re
import time
from pathlib import Path

import pytest

from appium import webdriver
from appium.options.ios import XCUITestOptions
from appium.webdriver.common.appiumby import AppiumBy

from selenium.webdriver.support.ui import WebDriverWait
from selenium.common.exceptions import TimeoutException


# ==================================================
# 1. SAUCE LABS CONFIGURATION
# ==================================================

SAUCE_URL = (
    "https://ondemand.us-west-1.saucelabs.com/wd/hub"
)

APP = os.getenv(
    "SAUCE_IOS_APP",
    "storage:filename=iOS-Real-Device-MyRNDemoApp.ipa",
)

DEVICE = os.getenv(
    "SAUCE_IOS_DEVICE",
    "iPhone.*",
)

TIMEOUT = 25

SCREENSHOT_DIR = Path("screenshots")


def create_driver(test_name):
    """
    Create a remote Appium session on
    Sauce Labs Real Device Cloud.
    """

    username = os.getenv("SAUCE_USERNAME")
    access_key = os.getenv("SAUCE_ACCESS_KEY")

    if not username or not access_key:
        raise RuntimeError(
            "SAUCE_USERNAME and SAUCE_ACCESS_KEY "
            "environment variables must be set."
        )

    capabilities = {
        "platformName": "iOS",

        "appium:automationName": "XCUITest",
        "appium:deviceName": DEVICE,
        "appium:app": APP,

        "sauce:options": {
            "username": username,
            "accessKey": access_key,
            "appiumVersion": "latest",
            "build": "RN-Demo-iOS-Python",
            "name": test_name,
        },
    }

    # Optional specific iOS version
    platform_version = os.getenv(
        "SAUCE_IOS_VERSION"
    )

    if platform_version:
        capabilities[
            "appium:platformVersion"
        ] = platform_version

    options = XCUITestOptions()
    options.load_capabilities(capabilities)

    session = webdriver.Remote(
        command_executor=SAUCE_URL,
        options=options,
    )

    print(
        f"\nSauce Labs session: "
        f"{session.session_id}"
    )

    return session


# ==================================================
# 2. PYTEST FIXTURE
# ==================================================

@pytest.fixture
def driver(request):
    """
    Start a Sauce Labs session for each test.

    The pytest hook in conftest.py records
    the test outcome as request.node.rep_call.

    The fixture reports that result to Sauce Labs
    and releases the remote device.
    """

    session = create_driver(
        request.node.name
    )

    yield session

    # Read pytest result
    report = getattr(
        request.node,
        "rep_call",
        None,
    )

    passed = (
        report is not None
        and report.passed
    )

    status = (
        "passed" if passed else "failed"
    )

    print(
        f"\nPytest result: "
        f"{report.outcome if report else 'unknown'}"
    )

    print(
        f"Reporting to Sauce Labs: {status}"
    )

    print(
        f"Sauce Labs session: "
        f"{session.session_id}"
    )

    # Screenshot for any failed test
    if not passed:
        SCREENSHOT_DIR.mkdir(
            parents=True,
            exist_ok=True,
        )

        screenshot_path = (
            SCREENSHOT_DIR /
            f"{request.node.name}.png"
        )

        try:
            session.save_screenshot(
                str(screenshot_path)
            )

            print(
                f"Screenshot saved: {screenshot_path}"
            )

        except Exception as error:
            print(
                f"Screenshot unavailable: {error}"
            )

    # Report status and release device
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
# 3. BASIC ELEMENT HELPERS
# ==================================================

def element(driver, accessibility_id):
    """
    Wait until an element with the given
    accessibility ID is visible.
    """

    def find_visible(d):
        matches = d.find_elements(
            AppiumBy.ACCESSIBILITY_ID,
            accessibility_id,
        )

        for match in matches:
            try:
                if match.is_displayed():
                    return match
            except Exception:
                continue

        return False

    return WebDriverWait(
        driver,
        TIMEOUT,
    ).until(find_visible)


def tap(driver, accessibility_id):
    """
    Tap an element using its accessibility ID.
    """

    target = element(
        driver,
        accessibility_id,
    )

    target.click()


def swipe_up(driver):
    """
    Scroll the iOS application upward
    using the XCUITest mobile swipe command.
    """

    driver.execute_script(
        "mobile: swipe",
        {
            "direction": "up",
        },
    )

    time.sleep(0.4)


def find_with_scroll(
    driver,
    accessibility_id,
    max_swipes=5,
):
    """
    Find a visible element.

    If necessary, scroll upward until
    the element becomes visible.
    """

    for attempt in range(
        max_swipes + 1
    ):
        matches = driver.find_elements(
            AppiumBy.ACCESSIBILITY_ID,
            accessibility_id,
        )

        for match in matches:
            try:
                if match.is_displayed():
                    return match
            except Exception:
                pass

        if attempt < max_swipes:
            print(
                f"Scrolling to find: "
                f"{accessibility_id}"
            )

            swipe_up(driver)

    raise AssertionError(
        f"Could not find visible element: "
        f"{accessibility_id}"
    )


def tap_with_scroll(
    driver,
    accessibility_id,
):
    """
    Find an element, scrolling if necessary,
    and tap it.
    """

    target = find_with_scroll(
        driver,
        accessibility_id,
    )

    target.click()


# ==================================================
# 4. IOS KEYBOARD HANDLING
# ==================================================

def keyboard_is_open(driver):
    """
    Return the reported iOS keyboard state.

    If Appium cannot determine the state,
    return None rather than assuming it is closed.
    """

    try:
        return driver.is_keyboard_shown()
    except Exception as error:
        print(
            f"Keyboard state unavailable: {error}"
        )

        return None


def dismiss_ios_keyboard(driver):
    """
    Attempt several supported ways of
    dismissing the iOS keyboard.

    This is particularly important when
    entering payment information because
    numeric keyboards may not have a
    standard Return or Done button.
    """

    state = keyboard_is_open(driver)

    if state is False:
        print("iOS keyboard already closed.")
        return True

    print("Attempting to dismiss iOS keyboard...")

    # Method 1: Appium hide_keyboard
    try:
        driver.hide_keyboard()
        time.sleep(0.4)

        if keyboard_is_open(driver) is False:
            print(
                "Keyboard dismissed with hide_keyboard."
            )
            return True

    except Exception as error:
        print(
            f"hide_keyboard unavailable: {error}"
        )

    # Method 2: Tap a keyboard dismissal key
    for label in (
        "Done",
        "Return",
    ):
        try:
            buttons = driver.find_elements(
                AppiumBy.ACCESSIBILITY_ID,
                label,
            )

            for button in buttons:
                if button.is_displayed():
                    button.click()
                    time.sleep(0.4)

                    if (
                        keyboard_is_open(driver)
                        is False
                    ):
                        print(
                            "Keyboard dismissed "
                            f"using {label}."
                        )
                        return True

        except Exception:
            pass

    # Method 3: Tap an unobstructed portion
    # of the screen above the keyboard.
    try:
        size = driver.get_window_size()

        driver.execute_script(
            "mobile: tap",
            {
                "x": int(
                    size["width"] * 0.5
                ),
                "y": int(
                    size["height"] * 0.15
                ),
            },
        )

        time.sleep(0.5)

        if keyboard_is_open(driver) is False:
            print(
                "Keyboard dismissed by tapping "
                "outside the input field."
            )
            return True

    except Exception as error:
        print(
            f"Outside tap unavailable: {error}"
        )

    # Final verification
    state = keyboard_is_open(driver)

    if state is True:
        print(
            "WARNING: iOS keyboard is still open."
        )
        return False

    if state is None:
        print(
            "WARNING: Cannot confirm whether "
            "the keyboard is closed."
        )
        return False

    return True


def type_text(
    driver,
    accessibility_id,
    value,
):
    """
    Find an input field, enter text,
    and attempt to dismiss the keyboard.
    """

    field = find_with_scroll(
        driver,
        accessibility_id,
    )

    field.clear()
    field.send_keys(value)

    dismiss_ios_keyboard(driver)


# ==================================================
# 5. CHECKOUT DEBUGGING HELPERS
# ==================================================

def save_checkout_debug(driver):
    """
    Save a screenshot and the iOS
    accessibility hierarchy.

    These artifacts help diagnose missing
    or obscured checkout controls.
    """

    SCREENSHOT_DIR.mkdir(
        parents=True,
        exist_ok=True,
    )

    screenshot_path = (
        SCREENSHOT_DIR /
        "ios-payment-debug.png"
    )

    source_path = (
        SCREENSHOT_DIR /
        "ios-payment-source.xml"
    )

    try:
        driver.save_screenshot(
            str(screenshot_path)
        )

        print(
            f"Debug screenshot saved: "
            f"{screenshot_path}"
        )

    except Exception as error:
        print(
            f"Screenshot capture failed: {error}"
        )

    try:
        source_path.write_text(
            driver.page_source,
            encoding="utf-8",
        )

        print(
            f"Page source saved: "
            f"{source_path}"
        )

    except Exception as error:
        print(
            f"Page source capture failed: {error}"
        )


def tap_review_order(driver):
    """
    Locate and tap the Review Order button.

    The button is in the payment screen's
    footer, not inside its scrolling form.

    Avoid unnecessary swiping, dismiss
    the keyboard, and capture diagnostics
    if the button remains inaccessible.
    """

    print(
        "Preparing to select Review Order..."
    )

    # Close keyboard before trying
    dismiss_ios_keyboard(driver)

    # Let layout settle after keyboard closes
    time.sleep(0.5)

    buttons = driver.find_elements(
        AppiumBy.ACCESSIBILITY_ID,
        "Review Order button",
    )

    print(
        f"Review Order buttons found: "
        f"{len(buttons)}"
    )

    # Attempt to find a displayed button
    for button in buttons:
        try:
            if button.is_displayed():
                print(
                    "Review Order button is visible."
                )

                button.click()

                element(
                    driver,
                    "checkout review order screen",
                )

                print(
                    "Successfully reached "
                    "order review screen."
                )

                return

        except Exception as error:
            print(
                f"Button interaction failed: "
                f"{error}"
            )

    # Retry once after another dismissal
    print(
        "Review Order button not accessible. "
        "Retrying keyboard dismissal..."
    )

    dismiss_ios_keyboard(driver)

    try:
        review_button = element(
            driver,
            "Review Order button",
        )

        review_button.click()

        element(
            driver,
            "checkout review order screen",
        )

        print(
            "Successfully reached "
            "order review screen."
        )

        return

    except Exception as error:
        print(
            f"Review Order retry failed: {error}"
        )

    # If still unsuccessful, save evidence
    save_checkout_debug(driver)

    raise AssertionError(
        "Could not interact with the "
        "Review Order button or reach "
        "the order review screen. "
        "Inspect screenshots/ios-payment-debug.png "
        "and screenshots/ios-payment-source.xml."
    )


# ==================================================
# 6. SHOPPING HELPERS
# ==================================================

def open_first_product(driver):
    """
    Open the first product in the catalog.
    """

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
    """
    Add Sauce Labs Backpack to the cart.
    """

    open_first_product(driver)

    price = element(
        driver,
        "product price",
    )

    assert price.text.strip() == "$29.99"

    tap_with_scroll(
        driver,
        "Add To Cart button",
    )

    # Wait for React Native cart animation
    time.sleep(0.8)

    # iOS opens cart through its tab bar
    tap(
        driver,
        "tab bar option cart",
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
    """
    Add a product and navigate to login
    through the checkout flow.
    """

    add_backpack_to_cart(driver)

    tap_with_scroll(
        driver,
        "Proceed To Checkout button",
    )

    element(
        driver,
        "login screen",
    )


def get_cart_quantity(driver):
    """
    Read the quantity from the cart counter.

    On iOS, accessibility text may be
    exposed as a label, value, or
    descendant StaticText.
    """

    counter = element(
        driver,
        "counter amount",
    )

    candidates = [counter]

    candidates.extend(
        counter.find_elements(
            AppiumBy.CLASS_NAME,
            "XCUIElementTypeStaticText",
        )
    )

    for candidate in candidates:

        for attribute in (
            "value",
            "label",
            "name",
        ):
            try:
                value = candidate.get_attribute(
                    attribute
                )

                if (
                    value
                    and value.strip().isdigit()
                ):
                    return int(value.strip())

            except Exception:
                pass

        try:
            text = candidate.text.strip()

            if text.isdigit():
                return int(text)

        except Exception:
            pass

    raise AssertionError(
        "Could not read the iOS cart quantity. "
        "Inspect the counter amount element "
        "in Appium Inspector."
    )


# ==================================================
# 7. TEST CASES
# ==================================================

# --------------------------------------------------
# TEST 1: PRODUCT CATALOG
# --------------------------------------------------

def test_ios_product_catalog(driver):

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
        name.text
        for name in names
    ]

    assert (
        "Sauce Labs Backpack"
        in product_names
    )

    print(
        "PASS: iOS product catalog verified"
    )


# --------------------------------------------------
# TEST 2: PRODUCT DETAILS
# --------------------------------------------------

def test_ios_product_details(driver):

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

    print(
        "PASS: iOS product details verified"
    )


# --------------------------------------------------
# TEST 3: SHOPPING CART
# --------------------------------------------------

def test_ios_shopping_cart(driver):

    add_backpack_to_cart(driver)

    # Verify initial quantity
    quantity = get_cart_quantity(driver)

    assert quantity == 1, (
        f"Expected quantity 1, got {quantity}"
    )

    print(
        "Initial cart quantity verified: 1"
    )

    # Increase quantity
    tap(
        driver,
        "counter plus button",
    )

    WebDriverWait(
        driver,
        TIMEOUT,
    ).until(
        lambda d: (
            get_cart_quantity(d) == 2
        )
    )

    quantity = get_cart_quantity(driver)

    assert quantity == 2, (
        f"Expected quantity 2, got {quantity}"
    )

    print(
        "Updated cart quantity verified: 2"
    )

    # Remove product
    tap(
        driver,
        "remove item",
    )

    empty_cart = element(
        driver,
        "Go Shopping button",
    )

    assert empty_cart.is_displayed()

    print(
        "PASS: iOS shopping cart verified"
    )


# --------------------------------------------------
# TEST 4: LOCKED-OUT LOGIN
# --------------------------------------------------

def test_ios_locked_out_login(driver):

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

    tap_with_scroll(
        driver,
        "Login button",
    )

    error = WebDriverWait(
        driver,
        TIMEOUT,
    ).until(
        lambda d: d.find_element(
            AppiumBy.IOS_PREDICATE,
            'label == "Sorry, this user has been locked out."',
        )
    )

    assert error.is_displayed()

    print(
        "PASS: iOS locked-out login verified"
    )


# --------------------------------------------------
# TEST 5: EMPTY LOGIN VALIDATION
# --------------------------------------------------

def test_ios_empty_login_validation(driver):

    open_login_from_cart(driver)

    tap_with_scroll(
        driver,
        "Login button",
    )

    error = WebDriverWait(
        driver,
        TIMEOUT,
    ).until(
        lambda d: d.find_element(
            AppiumBy.IOS_PREDICATE,
            'label == "Username is required"',
        )
    )

    assert error.is_displayed()

    print(
        "PASS: iOS login validation verified"
    )


# --------------------------------------------------
# TEST 6: COMPLETE CHECKOUT
# --------------------------------------------------

def test_ios_complete_checkout(driver):

    print(
        "Starting complete iOS checkout test..."
    )

    # ----------------------------------------------
    # STEP 1: ADD PRODUCT
    # ----------------------------------------------

    open_login_from_cart(driver)

    # ----------------------------------------------
    # STEP 2: LOGIN
    # ----------------------------------------------

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

    tap_with_scroll(
        driver,
        "Login button",
    )

    element(
        driver,
        "checkout address screen",
    )

    print("Login successful.")

    # ----------------------------------------------
    # STEP 3: SHIPPING ADDRESS
    # ----------------------------------------------

    shipping_fields = {
        "Full Name* input field":
            "Rebecca Winter",

        "Address Line 1* input field":
            "Mandorley 112",

        "City* input field":
            "Truro",

        "Zip Code* input field":
            "89750",

        "Country* input field":
            "United Kingdom",
    }

    for name, value in shipping_fields.items():

        print(
            f"Entering shipping field: {name}"
        )

        type_text(
            driver,
            name,
            value,
        )

    dismiss_ios_keyboard(driver)

    tap_with_scroll(
        driver,
        "To Payment button",
    )

    element(
        driver,
        "checkout payment screen",
    )

    print(
        "Shipping address accepted."
    )

    # ----------------------------------------------
    # STEP 4: PAYMENT DETAILS
    # ----------------------------------------------

    payment_fields = {
        "Full Name* input field":
            "Rebecca Winter",

        "Card Number* input field":
            "5555555555554444",

        "Expiration Date* input field":
            "1230",

        "Security Code* input field":
            "123",
    }

    for name, value in payment_fields.items():

        print(
            f"Entering payment field: {name}"
        )

        type_text(
            driver,
            name,
            value,
        )

    print(
        "Payment fields entered."
    )

    # Explicitly dismiss the keyboard
    dismiss_ios_keyboard(driver)

    # ----------------------------------------------
    # STEP 5: REVIEW ORDER
    # ----------------------------------------------

    # Use the dedicated footer-button helper.
    # Do not repeatedly swipe the payment form.
    tap_review_order(driver)

    # ----------------------------------------------
    # STEP 6: PLACE ORDER
    # ----------------------------------------------

    tap_with_scroll(
        driver,
        "Place Order button",
    )

    # ----------------------------------------------
    # STEP 7: VERIFY CONFIRMATION
    # ----------------------------------------------

    confirmation = element(
        driver,
        "checkout complete screen",
    )

    assert confirmation.is_displayed()

    continue_button = element(
        driver,
        "Continue Shopping button",
    )

    assert continue_button.is_displayed()

    print(
        "PASS: Complete iOS checkout verified"
    )
