import importlib.util
import json
import warnings
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]


def verify(root):
    spec = importlib.util.spec_from_file_location("web_asset_build", root / "apps/web/build.py")
    builder = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(builder)
    output = builder.build()
    report = []
    for name, expected in builder.ASSETS.items():
        source = root / "pb_public" / name
        copied = output / "assets" / name
        data = copied.read_bytes()
        if data != source.read_bytes() or builder.git_blob_sha(data) != expected:
            raise ValueError(f"Copied asset mismatch: {name}")
        with warnings.catch_warnings():
            warnings.simplefilter("error", Image.DecompressionBombWarning)
            with Image.open(copied) as image:
                if image.format != "PNG":
                    raise ValueError(f"Not PNG: {name}")
                image.verify()
            with Image.open(copied) as image:
                image.load()
                width, height = image.size
                if width < 1 or height < 1:
                    raise ValueError(f"Empty image: {name}")
        report.append({"name": name, "bytes": len(data), "git_blob_sha": expected,
                       "width": width, "height": height})
    return {"web_build": "passed", "images": report, "android_build": "not_run"}


if __name__ == "__main__":
    print(json.dumps(verify(ROOT), indent=2))
