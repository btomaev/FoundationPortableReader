import importlib
import platform


arch = platform.machine()

ftml = None
if arch in ["x86_64", "aarch64"]:
    ftml = importlib.import_module(f'ftml.{arch}.libftml')
else:
    raise RuntimeError(f"Unsupported architecture: {arch}")