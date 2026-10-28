import os
import sys

# Set PYTHONHOME and paths for Android
if hasattr(sys, 'getandroidapilevel') or 'ANDROID_DATA' in os.environ:
    base = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    stdlib = os.path.join(base, 'python311-stdlib')
    if stdlib not in sys.path:
        sys.path.insert(0, stdlib)
