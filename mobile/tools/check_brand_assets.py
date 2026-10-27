"""Check generated vectors against the approved SVG without accessing a device."""
import io
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from PIL import Image, ImageChops, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / '.tmp/icon-vector-tools'))
import resvg_py

A = '{http://schemas.android.com/apk/res/android}'
RES = ROOT / 'mobile/android/app/src/main/res/drawable'
SOURCE = ROOT / 'app/qml/assets/numa-logo.svg'


def render_vector(path, size):
    root = ET.parse(path).getroot()
    svg = ET.Element('svg', xmlns='http://www.w3.org/2000/svg',
        viewBox=f'0 0 {root.get(A + "viewportWidth")} {root.get(A + "viewportHeight")}')
    defs = ET.SubElement(svg, 'defs')

    def visit(node, target):
        for child in node:
            if child.tag == 'group':
                group = ET.SubElement(target, 'g', transform=f'translate({child.get(A + "translateX", "0")},{child.get(A + "translateY", "0")})')
                visit(child, group)
            if child.tag != 'path':
                continue
            attrs = {'d': child.get(A + 'pathData'), 'fill-rule': 'evenodd' if child.get(A + 'fillType') == 'evenOdd' else 'nonzero'}
            paint = child.find('.//gradient')
            if paint is not None:
                key = f'paint{len(defs)}'
                gradient = ET.SubElement(defs, 'linearGradient', id=key, gradientUnits='userSpaceOnUse',
                    x1=paint.get(A + 'startX'), y1=paint.get(A + 'startY'), x2=paint.get(A + 'endX'), y2=paint.get(A + 'endY'))
                for stop in paint:
                    ET.SubElement(gradient, 'stop', offset=stop.get(A + 'offset'), **{'stop-color': stop.get(A + 'color')})
                attrs['fill'] = f'url(#{key})'
            else:
                color = child.get(A + 'fillColor', '#000000')
                attrs['fill'] = '#' + color[3:] if len(color) == 9 else color
            ET.SubElement(target, 'path', attrs)
    visit(root, svg)
    return Image.open(io.BytesIO(resvg_py.svg_to_bytes(svg_string=ET.tostring(svg, encoding='unicode'), width=size, height=size))).convert('RGBA')


approved = Image.open(io.BytesIO(resvg_py.svg_to_bytes(svg_path=str(SOURCE), width=1024, height=1024))).convert('RGBA')
native = render_vector(RES / 'numa_mark.xml', 1024)
assert ImageChops.difference(approved, native).getbbox(alpha_only=False) is None, 'Android vector differs from approved SVG'
rounded = render_vector(RES / 'numa_mark_rounded.xml', 1024)
rounded_svg = Image.open(io.BytesIO(resvg_py.svg_to_bytes(svg_path=str(SOURCE.with_name('numa-logo-rounded.svg')), width=1024, height=1024))).convert('RGBA')
assert ImageChops.difference(rounded, rounded_svg).getbbox(alpha_only=False) is None, 'Rounded Android and SVG differ'
assert rounded.getpixel((0, 0))[3] == 0
assert rounded.getpixel((512, 30))[3] == 255
for file in ['app/qml/assets/numa-logo.png', 'mobile/android/app/src/main/res/drawable-nodpi/numa_logo.png', 'mobile/assets/icon.png']:
    assert ImageChops.difference(approved, Image.open(ROOT / file).convert('RGBA')).getbbox(alpha_only=False) is None, file
for file in ['app/qml/assets/numa.ico', 'app/src/platform/windows/numa.ico', 'bootstrapper/src/platform/windows/numa.ico']:
    assert (256, 256) in Image.open(ROOT / file).info['sizes'], file
mono = render_vector(RES / 'ic_notification.xml', 1254)
assert mono.getpixel((627, 700))[3] == 0, 'Monochrome bars must remain visible as cutouts'
assert mono.getpixel((627, 435))[3] > 200, 'Monochrome outline is missing'

preview = Image.new('RGB', (720, 260), '#eeeeF4')
for x, name, background in [(0, 'numa_mark.xml', '#eeeeF4'), (240, 'numa_launcher_layer.xml', '#7474FF'), (480, 'ic_notification.xml', '#20212B')]:
    preview.paste(background, (x + 20, 20, x + 220, 220))
    icon = render_vector(RES / name, 200)
    preview.paste(icon, (x + 20, 20), icon)
    ImageDraw.Draw(preview).text((x + 50, 235), name.removesuffix('.xml'), fill='#15151d')
output = ROOT / 'outputs/numa-vector/integrado.png'
preview.save(output)
print('SVG, Android vector, 3 PNGs, 3 ICOs and monochrome cutouts: OK')
