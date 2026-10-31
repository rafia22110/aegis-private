import hashlib
import shutil
from pathlib import Path

WEB = Path(__file__).resolve().parent
ROOT = WEB.parents[1]
ASSETS = {
    "logo.png": "0e350bedd459fe35f65f69b12310c4ad396f72df",
    "medusa-core.png": "6b4c1e98afb5e39268a44b0580834bb311e1c072",
}


def git_blob_sha(data):
    return hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest()


def build():
    sources = {}
    for name, expected in ASSETS.items():
        data = (ROOT / "pb_public" / name).read_bytes()
        if git_blob_sha(data) != expected:
            raise ValueError(f"Asset differs from reviewed source: {name}")
        sources[name] = data
    pages = {name: (WEB / name).read_bytes()
             for name in ("index.html", "styles.css", "manifest.webmanifest")}
    output = WEB / "dist"
    if output.is_symlink():
        raise ValueError("Refusing a symlink output directory")
    if output.exists():
        shutil.rmtree(output)
    (output / "assets").mkdir(parents=True)
    for name, data in pages.items():
        (output / name).write_bytes(data)
    for name, data in sources.items():
        (output / "assets" / name).write_bytes(data)
    return output


if __name__ == "__main__":
    print(build())
