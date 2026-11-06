
import pytest


@pytest.hookimpl(hookwrapper=True)
def pytest_runtest_makereport(item, call):
    """
    Capture the results of each pytest phase
    so fixtures can report the correct test
    status to Sauce Labs.
    """

    outcome = yield
    report = outcome.get_result()

    setattr(
        item,
        "rep_" + report.when,
        report
    )
