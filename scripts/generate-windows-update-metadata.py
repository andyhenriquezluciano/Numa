"""Generate Windows-only updater metadata from the published installer and notes."""

import argparse
import hashlib
import json
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--version', required=True)
parser.add_argument('--installer', type=Path, required=True)
parser.add_argument('--url', required=True)
parser.add_argument('--notes', type=Path, required=True)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
args.output.write_text(json.dumps({
    'platform': 'windows',
    'version': args.version,
    'installerUrl': args.url,
    'sha256': hashlib.file_digest(args.installer.open('rb'), 'sha256').hexdigest(),
    'releaseNotes': args.notes.read_text(encoding='utf-8'),
}, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
