#!/usr/bin/env python3
"""Package the verified local Android APK and the complete Windows runtime."""

import argparse
import hashlib
import re
from pathlib import Path
import shutil
import zipfile


def sha256(path: Path) -> str:
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--desktop', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--version', default='2.0')
    parser.add_argument('--windows-version')
    args = parser.parse_args()
    if not re.fullmatch(r'\d+\.\d+(?:\.\d+){0,2}', args.version):
        raise SystemExit('Invalid release version')
    windows_version = args.windows_version or args.version
    if not re.fullmatch(r'\d+\.\d+(?:\.\d+){0,2}', windows_version):
        raise SystemExit('Invalid Windows version')
    root = Path(__file__).resolve().parent.parent
    apk = args.apk.resolve(strict=True)
    desktop = args.desktop.resolve(strict=True)
    output = args.output.resolve()
    required = ('numa.exe', 'numa-auth-helper.exe', 'numa-adaptive-mix.exe',
                'QtWebEngineProcess.exe', 'ffmpeg.exe', 'platforms/qwindows.dll')
    for relative in required:
        if not (desktop / relative).is_file():
            raise SystemExit(f'Missing Windows runtime component: {relative}')
    runtime = sorted(path for path in desktop.rglob('*') if path.is_file())
    forbidden = ('.db', '.sqlite', '.sqlite3', '.log', '.pem', '.key', '.keystore', '.jks')
    for path in runtime:
        if path.is_symlink() or path.suffix.lower() in forbidden or path.name.startswith('.env'):
            raise SystemExit(f'Private or unexpected runtime file: {path.relative_to(desktop)}')
    names = (f'Numa-Android-{args.version}.apk', f'Numa-Windows-{windows_version}-x64.zip', 'SHA256SUMS.txt')
    if any((output / name).exists() for name in names):
        raise SystemExit('Refusing to overwrite an existing release.')
    output.mkdir(parents=True, exist_ok=True)
    shutil.copy2(apk, output / names[0])
    with zipfile.ZipFile(output / names[1], 'w', compression=zipfile.ZIP_DEFLATED,
                         compresslevel=6, allowZip64=True) as archive:
        for path in runtime:
            archive.write(path, 'Numa/' + path.relative_to(desktop).as_posix())
        for name in ('LICENSE', 'LICENSE-EXCEPTION.md', 'LICENSE-OOL', 'THIRD_PARTY_NOTICES.md'):
            if not (desktop / name).is_file():
                archive.write(root / name, 'Numa/' + name)
    with zipfile.ZipFile(output / names[1]) as archive:
        damaged = archive.testzip()
        if damaged:
            raise SystemExit(f'Archive CRC check failed: {damaged}')
    if sha256(apk) != sha256(output / names[0]):
        raise SystemExit('APK copy checksum mismatch.')
    checksums = ''.join(f'{sha256(output / name)}  {name}\n' for name in names[:2])
    (output / names[2]).write_text(checksums, encoding='utf-8')
    print(checksums, end='')
    print(f'Windows runtime: {len(runtime)} files; archive CRC verified.')


if __name__ == '__main__':
    main()
