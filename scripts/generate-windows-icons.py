"""Generate Windows icons from the rounded SVG shared with Android branding."""

import io
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / ".tmp/icon-vector-tools"))

from PIL import Image
import resvg_py


def main():
    source = ROOT / "app/qml/assets/numa-logo-rounded.svg"
    png = resvg_py.svg_to_bytes(svg_path=str(source), width=1024, height=1024)
    image = Image.open(io.BytesIO(png)).convert("RGBA")
    for relative in (
        "app/qml/assets/numa.ico",
        "app/src/platform/windows/numa.ico",
        "bootstrapper/src/platform/windows/numa.ico",
    ):
        image.save(ROOT / relative, format="ICO",
                   sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
        print(relative)


if __name__ == "__main__":
    main()
