import os
import sys

_TESTS_DIR = os.path.dirname(os.path.abspath(__file__))
_PROJECT_DIR = os.path.dirname(_TESTS_DIR)

# The project root makes `import app` work; the tests dir lets tests in subpackages share
# this module's helpers regardless of which directory they live in.
sys.path.insert(0, _PROJECT_DIR)
sys.path.insert(0, _TESTS_DIR)

DATA_DIR = os.path.join(os.path.dirname(_PROJECT_DIR), "data")
REQUESTS_DIR = os.path.join(DATA_DIR, "requests")


def read_request(name: str) -> bytes:
    with open(os.path.join(REQUESTS_DIR, name), "rb") as handle:
        return handle.read()
