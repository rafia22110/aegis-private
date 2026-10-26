import importlib.util
import json
import tempfile
import unittest
from html.parser import HTMLParser
from pathlib import Path
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
WEB = ROOT / "apps/web"
spec = importlib.util.spec_from_file_location("aegis_web_build", WEB / "build.py")
builder = importlib.util.module_from_spec(spec)
spec.loader.exec_module(builder)


class Markup(HTMLParser):
    def __init__(self):
        super().__init__()
        self.tags = []

    def handle_starttag(self, tag, attrs):
        self.tags.append((tag, dict(attrs)))


class WebTests(unittest.TestCase):
    def test_static_markup_has_no_active_scripts_or_forms(self):
        page = Markup()
        page.feed((WEB / "index.html").read_text(encoding="utf-8"))
        self.assertFalse(any(tag in {"script", "form", "iframe"} for tag, _ in page.tags))
        self.assertFalse(any(key.startswith("on") for _, attrs in page.tags for key in attrs))
        csp = next(attrs["content"] for tag, attrs in page.tags if tag == "meta" and attrs.get("http-equiv") == "Content-Security-Policy")
        self.assertIn("connect-src 'none'", csp)
        self.assertIn("script-src 'none'", csp)

    def test_controls_are_disabled_and_urls_are_relative(self):
        page = Markup()
        page.feed((WEB / "index.html").read_text(encoding="utf-8"))
        buttons = [attrs for tag, attrs in page.tags if tag == "button"]
        self.assertTrue(buttons)
        self.assertTrue(all("disabled" in attrs for attrs in buttons))
        for _, attrs in page.tags:
            for key in ("src", "href"):
                if key in attrs:
                    self.assertFalse(attrs[key].startswith(("/", "http:", "https:", "//")))
        manifest = json.loads((WEB / "manifest.webmanifest").read_text())
        self.assertEqual(manifest["scope"], "./")
        self.assertEqual(manifest["start_url"], "./")

    def test_git_blob_hash(self):
        self.assertEqual(builder.git_blob_sha(b""), "e69de29bb2d1d6434b8b29ae775ad8c2e48c5391")

    def test_fixture_build_copies_all_local_files(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            web = root / "apps/web"
            web.mkdir(parents=True)
            (root / "pb_public").mkdir()
            for name in ("index.html", "styles.css", "manifest.webmanifest"):
                (web / name).write_bytes((WEB / name).read_bytes())
            data = b"fixture-image-not-a-real-png"
            (root / "pb_public/logo.png").write_bytes(data)
            with patch.object(builder, "ROOT", root), patch.object(builder, "WEB", web), patch.object(builder, "ASSETS", {"logo.png": builder.git_blob_sha(data)}):
                output = builder.build()
                self.assertEqual((output / "assets/logo.png").read_bytes(), data)
                self.assertTrue((output / "index.html").exists())
                self.assertEqual(builder.build(), output)

    def test_bad_asset_does_not_destroy_existing_output(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            web = root / "apps/web"
            (web / "dist").mkdir(parents=True)
            (web / "dist/keep.txt").write_text("keep")
            (root / "pb_public").mkdir()
            (root / "pb_public/logo.png").write_bytes(b"wrong")
            with patch.object(builder, "ROOT", root), patch.object(builder, "WEB", web), patch.object(builder, "ASSETS", {"logo.png": "wrong-hash"}):
                with self.assertRaises(ValueError):
                    builder.build()
            self.assertEqual((web / "dist/keep.txt").read_text(), "keep")


if __name__ == "__main__":
    unittest.main()
