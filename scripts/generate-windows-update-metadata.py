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
parser.add_argument('--space-legacy-notes', action='store_true',
                    help='Preserve spacing between note bullets in older Windows clients.')
args = parser.parse_args()
notes = args.notes.read_text(encoding='utf-8')
if args.space_legacy_notes:
    # Older Qt Markdown views collapse blank lines between list items.
    notes = notes.replace('\n\n- ', '\n\n&nbsp;\n\n- ')
args.output.write_text(json.dumps({
    'platform': 'windows',
    'version': args.version,
    'installerUrl': args.url,
    'installProtocol': 'nsis-wait-v1',
    'sha256': hashlib.file_digest(args.installer.open('rb'), 'sha256').hexdigest(),
    'releaseNotes': notes,
}, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
