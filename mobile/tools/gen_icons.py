# Copyright (C) 2026 SFG545
#
# This file is part of Numa.
#
# Numa is free software: you can redistribute it and/or modify it under the
# terms of the GNU Affero General Public License as published by the Free
# Software Foundation, either version 3 of the License, or (at your option) any
# later version.
#
# Numa is distributed in the hope that it will be useful, but WITHOUT ANY
# WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
# A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
# details.
#
# You should have received a copy of the GNU Affero General Public License
# along with Numa. If not, see <https://www.gnu.org/licenses/>.

"""Generate brand resources from SVG. Requires Pillow and resvg-py."""
import io
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "mobile" / "assets"
SOURCE = ROOT / "app" / "qml" / "assets" / "numa-logo.svg"
sys.path.insert(0, str(ROOT / '.tmp/icon-vector-tools'))
import resvg_py

ANDROID = ROOT / 'mobile/android/app/src/main'
NS = 'http://schemas.android.com/apk/res/android'
AAPT = 'http://schemas.android.com/aapt'
SVG = {'s': 'http://www.w3.org/2000/svg'}
ET.register_namespace('android', NS)
ET.register_namespace('aapt', AAPT)


def attr(**values):
    return {f'{{{NS}}}{key}': str(value) for key, value in values.items()}


def translation(value):
    if not value:
        return 0., 0.
    match = re.fullmatch(r'translate\(([-.\d]+)[, ]+([-.\d]+)\)', value)
    if not match:
        raise ValueError(f'Unsupported transform: {value}')
    return tuple(map(float, match.groups()))


def absolute_path(data, x, y):
    tokens = re.findall(r'[A-Za-z]|[-+]?(?:\d*\.\d+|\d+)(?:[eE][-+]?\d+)?', data)
    coordinate = 0
    result = []
    for token in tokens:
        if token.isalpha():
            if token not in ('M', 'L', 'C', 'Z'):
                raise ValueError(f'Unsupported path command: {token}')
            result.append(token)
            coordinate = 0
        else:
            result.append(f'{float(token) + (x if coordinate % 2 == 0 else y):.3f}')
            coordinate += 1
    return ' '.join(result)


def vector_resources():
    svg = ET.parse(SOURCE).getroot()
    gradients = {g.get('id'): g for g in svg.findall('.//s:linearGradient', SVG)}
    paths = []

    def visit(node, x=0., y=0.):
        tx, ty = translation(node.get('transform', ''))
        x, y = x + tx, y + ty
        if node.tag.endswith('}path'):
            paths.append((node, x, y))
        for child in node:
            if not child.tag.endswith('}defs'):
                visit(child, x, y)
    visit(svg)

    def export(filename, monochrome=False, launcher=False, size=48, rounded=False):
        extent = 1881 if launcher else 1254
        vector = ET.Element('vector', attr(width=f'{size}dp', height=f'{size}dp',
            viewportWidth=extent, viewportHeight=extent))
        parent = ET.SubElement(vector, 'group', attr(translateX=313.5, translateY=313.5)) if launcher else vector
        if rounded:
            ET.SubElement(parent, 'path', attr(fillColor='#7474FF', pathData=
                'M260,0 L994,0 A260,260 0 0 1 1254,260 L1254,994 A260,260 0 0 1 994,1254 L260,1254 A260,260 0 0 1 0,994 L0,260 A260,260 0 0 1 260,0 Z'))
        if monochrome:
            compound = ' '.join(absolute_path(p.get('d'), x, y) for p, x, y in paths)
            ET.SubElement(parent, 'path', attr(pathData=compound, fillColor='#FFFFFFFF', fillType='evenOdd'))
        else:
            for path, x, y in paths:
                group = ET.SubElement(parent, 'group', attr(translateX=x, translateY=y))
                output = ET.SubElement(group, 'path', attr(pathData=path.get('d')))
                fill = path.get('fill')
                if not fill.startswith('url('):
                    output.set(f'{{{NS}}}fillColor', fill)
                    continue
                source = gradients[fill[5:-1]]
                dx, dy = translation(source.get('gradientTransform', ''))
                paint = ET.SubElement(output, f'{{{AAPT}}}attr', name='android:fillColor')
                gradient = ET.SubElement(paint, 'gradient', attr(type='linear',
                    startX=float(source.get('x1')) + dx, startY=float(source.get('y1')) + dy,
                    endX=float(source.get('x2')) + dx, endY=float(source.get('y2')) + dy))
                for stop in source:
                    ET.SubElement(gradient, 'item', attr(offset=stop.get('offset'), color=stop.get('stop-color')))
        ET.ElementTree(vector).write(ANDROID / 'res/drawable' / filename, encoding='utf-8', xml_declaration=True)

    export('numa_mark.xml')
    export('numa_mark_rounded.xml', rounded=True)
    import copy
    rounded_svg = copy.deepcopy(svg)
    rounded_svg.insert(1, ET.Element(f'{{{SVG["s"]}}}rect', x='0', y='0', width='1254', height='1254', rx='260', fill='#7474FF'))
    ET.register_namespace('', SVG['s'])
    ET.ElementTree(rounded_svg).write(SOURCE.with_name('numa-logo-rounded.svg'), encoding='utf-8', xml_declaration=True)
    export('numa_launcher_layer.xml', launcher=True, size=108)
    export('ic_launcher_monochrome.xml', monochrome=True, launcher=True, size=108)
    export('ic_notification.xml', monochrome=True, size=24)
    compound = ' '.join(absolute_path(p.get('d'), x, y) for p, x, y in paths)
    monochrome_svg = ET.Element('svg', xmlns=SVG['s'], viewBox='0 0 1254 1254')
    ET.SubElement(monochrome_svg, 'path', d=compound, fill='white', **{'fill-rule': 'evenodd'})
    png = resvg_py.svg_to_bytes(svg_string=ET.tostring(monochrome_svg, encoding='unicode'), width=1024, height=1024)
    Image.open(io.BytesIO(png)).save(ASSETS / 'android-icon-monochrome.png')


def resized(size):
    return Image.open(io.BytesIO(resvg_py.svg_to_bytes(svg_path=str(SOURCE), width=size, height=size))).convert('RGBA')


def save(img, name):
    img.save(ASSETS / name)
    print("wrote", name, img.size)


foreground = resized(1024)

save(foreground, "android-icon-foreground.png")
save(Image.new("RGBA", (1024, 1024), (116, 116, 255, 255)), "android-icon-background.png")
save(foreground.copy(), "icon.png")
save(resized(48), "favicon.png")
save(foreground.copy(), "splash-icon.png")
vector_resources()
for path in [ROOT / 'app/qml/assets/numa-logo.png', ANDROID / 'res/drawable-nodpi/numa_logo.png',
             ANDROID / 'assets/numa-logo.png']:
    foreground.save(path)
resized(48).save(ANDROID / 'assets/favicon.png')
windows_icon = Image.open(io.BytesIO(resvg_py.svg_to_bytes(
    svg_path=str(SOURCE.with_name('numa-logo-rounded.svg')), width=1024, height=1024))).convert('RGBA')
for path in ['app/qml/assets/numa.ico', 'app/src/platform/windows/numa.ico',
             'bootstrapper/src/platform/windows/numa.ico']:
    windows_icon.save(ROOT / path, format='ICO', sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
print('Generated Android vectors, compatibility PNGs and Windows icons from', SOURCE)
