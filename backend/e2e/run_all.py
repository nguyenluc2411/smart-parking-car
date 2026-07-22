"""Run every e2e script in order; exit non-zero if any of them fails."""
import pathlib
import subprocess
import sys

HERE = pathlib.Path(__file__).parent
SCRIPTS = ["test_reservations.py", "test_slot_guards.py", "test_billing.py"]

failed = []
for script in SCRIPTS:
    print("\n" + "#" * 70)
    print(f"# {script}")
    print("#" * 70)
    # UTF-8 forced: the services answer in Vietnamese, and a failure message full of diacritics
    # must not die in the console encoder — that hides the very output you need.
    result = subprocess.run([sys.executable, str(HERE / script)], cwd=HERE,
                            env={**__import__("os").environ, "PYTHONIOENCODING": "utf-8"})
    if result.returncode != 0:
        failed.append(script)

print("\n" + "=" * 70)
if failed:
    print("FAILED: " + ", ".join(failed))
    sys.exit(1)
print(f"All {len(SCRIPTS)} e2e scripts passed")
