# Copyright (C) 2026 SFG545. Licensed under the Numa-Only License (OOL-1.0), see LICENSE-OOL.

"""Stage the worker, ORT's Dawn runtime, FFmpeg and model files beside numa."""
import argparse
import hashlib
from pathlib import Path
import shutil
import urllib.request
import zipfile

PLATFORMS = ("linux-x86_64", "linux-aarch64", "windows-x86_64-msvc", "windows-x86_64-gnu",
             "windows-aarch64-msvc", "macos-x86_64", "macos-aarch64")
p = argparse.ArgumentParser()
p.add_argument("--platform", required=True, choices=PLATFORMS)
p.add_argument("--profile", type=Path, required=True)
p.add_argument("--destination", type=Path, required=True)
p.add_argument("--models", type=Path, required=True)
p.add_argument("--cache", type=Path, required=True)
a = p.parse_args()
a.destination.mkdir(parents=True, exist_ok=True)
a.cache.mkdir(parents=True, exist_ok=True)
found = False
dxc = set()
for output in (a.profile / "build").glob("ort-sys-*/output"):
    for line in output.read_text().splitlines():
        if "rustc-link-search=" not in line:
            continue
        directory = Path(line.split("rustc-link-search=", 1)[1].removeprefix("native="))
        for library in directory.glob("*webgpu*dawn*"):
            if library.suffix in (".so", ".dll", ".dylib"):
                shutil.copy2(library, a.destination / library.name)
                found = True
        # Dawn's D3D12 backend loads DXC from beside the worker.
        for name in ("dxil.dll", "dxcompiler.dll"):
            if (directory / name).exists():
                shutil.copy2(directory / name, a.destination / name)
                dxc.add(name)
if not found:
    raise SystemExit("ort's Dawn runtime was not found in Cargo's linker output")
if a.platform == "windows-x86_64-msvc" and dxc != {"dxil.dll", "dxcompiler.dll"}:
    raise SystemExit("ort's DXC libraries were not found in Cargo's linker output")
# Static FFmpeg for Windows, where no system decoder is on PATH.
FFMPEG = {
    "windows-x86_64-msvc": ("autobuild-2026-10-11-13-04", "ffmpeg-n9.0.2-25-g67b60c310b-win64-gpl-9.0",
                            "b1860fbeb14e87d1de44aecc5eb9e25dfecc7ee8efa8d54250995a9acfbe6070"),
}
FFMPEG["windows-x86_64-gnu"] = FFMPEG["windows-x86_64-msvc"]
if a.platform in FFMPEG:
    installed_ffmpeg = Path.home() / "AppData/Local/Programs/Numa/components/ffmpeg/8.0.2-4/ffmpeg.exe"
    installed_probe = installed_ffmpeg.with_name("ffprobe.exe")
    if installed_ffmpeg.exists() and installed_probe.exists():
        for tool in (installed_ffmpeg, installed_probe):
            shutil.copy2(tool, a.destination / tool.name)
    else:
        tag, stem, digest = FFMPEG[a.platform]
        archive = a.cache / (stem + ".zip")
        if not archive.exists():
            data = urllib.request.urlopen(f"https://github.com/BtbN/FFmpeg-Builds/releases/download/{tag}/{stem}.zip", timeout=300).read()
            if hashlib.sha256(data).hexdigest() != digest:
                raise SystemExit("FFmpeg download checksum mismatch")
            archive.write_bytes(data)
        # Hash in chunks: the archive is ~185 MB.
        h = hashlib.sha256()
        with archive.open("rb") as f:
            for chunk in iter(lambda: f.read(1 << 20), b""):
                h.update(chunk)
        if h.hexdigest() != digest:
            raise SystemExit("FFmpeg cached checksum mismatch")
        with zipfile.ZipFile(archive) as z:
            for tool in ("ffmpeg.exe", "ffprobe.exe"):
                with z.open(f"{stem}/bin/{tool}") as source, (a.destination / tool).open("wb") as target:
                    shutil.copyfileobj(source, target)
worker = "numa-adaptive-mix" + (".exe" if a.platform.startswith("windows") else "")
shutil.copy2(a.profile / worker, a.destination / worker)
# Stage only the validated runtime graphs.
for relative in ("beat-this/beat_this_webgpu.onnx",
                 "vocal-separation/vocals_umxhq_fp32.onnx",
                 "docs-search/model_quantized.onnx", "docs-search/vocab.txt",
                 "slop/fakeprint_lr.f32"):
    target = a.destination / "models" / relative
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(a.models / relative, target)

# Retire only earlier generated runtime graphs when rebuilding this directory.
# Reused build directories may hold a wgpu-native library the worker never loads.
for name in ("libwgpu_native.so", "wgpu_native.dll", "libwgpu_native.dylib"):
    (a.destination / name).unlink(missing_ok=True)
for relative in ("beat-this/beat_this.onnx", "vocal-separation/vocals_umxhq_int8.onnx",
                 "vocal-separation/vocals_umxhq_fp32_fp16.onnx"):
    stale = a.destination / "models" / relative
    if stale.exists() and stale.resolve() != (a.models / relative).resolve():
        stale.unlink()
