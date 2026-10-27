"""Trace supplied artwork without replacing it with a hand-drawn approximation."""
import argparse
import sys
from pathlib import Path
import xml.etree.ElementTree as ET
import io
import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / '.tmp/icon-vector-tools'))
import vtracer
import resvg_py
import cv2


def trace_layer(mask, colors, output, name, svg, defs):
    mask = mask.astype(np.uint8) * 255
    raster = output.parent / f'{name}-mask.png'
    Image.fromarray(255 - mask).convert('RGB').save(raster)
    traced = output.parent / f'{name}-trace.svg'
    vtracer.convert_image_to_svg_py(str(raster), str(traced), colormode='binary',
        mode='spline', filter_speckle=16, corner_threshold=60, length_threshold=2,
        max_iterations=10, splice_threshold=45, path_precision=3)
    inside = cv2.erode(mask, np.ones((7, 7), np.uint8)) > 0
    y, x = np.where(inside)
    points = np.column_stack([np.ones(len(x)), x / 1254, y / 1254])
    fit = np.linalg.lstsq(points, colors[inside, :3], rcond=None)[0]
    direction = fit[1:].mean(axis=1)
    norm = np.linalg.norm(direction)
    direction = direction / norm if norm > 0.01 else np.array([0., 1.])
    projection = (points[:, 1:] @ direction)
    lo, hi = projection.min(), projection.max()
    perpendicular = points[:, 1:].mean(axis=0)
    start = perpendicular + (lo - perpendicular @ direction) * direction
    end = perpendicular + (hi - perpendicular @ direction) * direction
    gradient = ET.SubElement(defs, 'linearGradient', id=name, gradientUnits='userSpaceOnUse',
        x1=str(start[0] * 1254), y1=str(start[1] * 1254), x2=str(end[0] * 1254), y2=str(end[1] * 1254))
    for offset, point in [('0', start), ('1', end)]:
        rgb = np.clip(np.array([1., *point]) @ fit, 0, 255).astype(int)
        ET.SubElement(gradient, 'stop', offset=offset, **{'stop-color': '#%02x%02x%02x' % tuple(rgb)})
    for path in ET.parse(traced).getroot():
        # VTracer stores paths in local coordinates; gradients share the source coordinates.
        group = ET.SubElement(svg, 'g')
        transform = path.attrib.pop('transform', '')
        if transform:
            import re
            tx, ty = map(float, re.findall(r'[-+]?\d*\.?\d+', transform))
            shifted = ET.SubElement(group, 'g', transform=transform)
            import copy
            local_gradient = copy.deepcopy(gradient)
            local_gradient.set('id', name + str(len(svg)))
            local_gradient.set('gradientTransform', f'translate({-tx},{-ty})')
            defs.append(local_gradient)
            path.set('fill', f'url(#{local_gradient.get("id")})')
            shifted.append(path)
        else:
            path.set('fill', f'url(#{name})')
            group.append(path)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('source', type=Path)
    parser.add_argument('output', type=Path)
    parser.add_argument('--unify-gaps', action='store_true')
    args = parser.parse_args()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    original = Image.open(args.source).convert('RGBA')
    pixels = np.array(original)
    silhouette = pixels[:, :, 3] > 128
    blue = silhouette & (pixels[:, :, 2].astype(int) - pixels[:, :, 0] > 40)
    svg = ET.Element('svg', xmlns='http://www.w3.org/2000/svg', width='1254', height='1254', viewBox='0 0 1254 1254')
    defs = ET.SubElement(svg, 'defs')
    dark_mask = silhouette & ~blue
    dark_pixels = pixels.copy()
    dark_pixels[blue, :3] = np.median(pixels[dark_mask, :3], axis=0)
    if args.unify_gaps:
        kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (41, 41))
        unified = cv2.morphologyEx(silhouette.astype(np.uint8), cv2.MORPH_CLOSE, kernel) > 0
        dark_pixels[unified & ~silhouette, :3] = np.median(pixels[dark_mask, :3], axis=0)
        silhouette = unified
    trace_layer(silhouette, dark_pixels, args.output, 'outline', svg, defs)
    count, labels = cv2.connectedComponents(blue.astype(np.uint8))
    for index in range(1, count):
        mask = labels == index
        if mask.sum() > 100:
            trace_layer(mask, pixels, args.output, f'violet{index}', svg, defs)
    ET.ElementTree(svg).write(args.output, encoding='utf-8', xml_declaration=True)
    print(f'{args.output}: {args.output.stat().st_size} bytes')
    rendered = resvg_py.svg_to_bytes(svg_path=str(args.output))
    preview = Image.open(io.BytesIO(rendered)).convert('RGBA')
    preview.save(args.output.with_suffix('.png'))
    original = Image.open(args.source).convert('RGBA')
    a = np.array(original).astype(float)
    b = np.array(preview).astype(float)
    foreground = a[:, :, 3] > 128
    print('Mean RGB error:', np.abs(a[:, :, :3] - b[:, :, :3])[foreground].mean())
    print('Silhouette agreement:', np.mean((a[:, :, 3] > 128) == (b[:, :, 3] > 128)))
    comparison = Image.new('RGB', (1000, 550), '#ececf1')
    for offset, image, label in [(0, original, 'PNG original'), (500, preview, 'Vector SVG')]:
        thumbnail = image.resize((480, 480), Image.Resampling.LANCZOS)
        comparison.paste(thumbnail, (offset + 10, 40), thumbnail)
        ImageDraw.Draw(comparison).text((offset + 180, 15), label, fill='#15151d')
    comparison.save(args.output.parent / 'comparacion.png')
    if args.unify_gaps:
        small = Image.new('RGB', (600, 170), '#ececf1')
        for x, size in [(36, 32), (140, 48), (275, 64), (440, 96)]:
            thumbnail = preview.resize((size, size), Image.Resampling.LANCZOS)
            small.paste(thumbnail, (x, 36), thumbnail)
            ImageDraw.Draw(small).text((x, 140), f'{size}px', fill='#15151d')
        small.save(args.output.parent / 'tamanos.png')


if __name__ == '__main__':
    main()
