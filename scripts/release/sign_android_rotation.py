#!/usr/bin/env python3
"""Sign the NÜMA Android release for both Android 12 and Android 13+."""

import argparse
import os
from pathlib import Path
import re
import subprocess
import tempfile


ORIGINAL_CERT = "91197858b1ff7ad67c876826f758b7dab2632d53e01e2e66bd0b895f5b421bc3"
PRODUCTION_CERT = "a0b9dd81aad57164108a07e7b0afa5157f6c1cbc9dcbad719070e2b9a61e1e41"
ROTATION_API = 33


def run(*args):
    return subprocess.run(args, check=True, stdout=subprocess.PIPE, text=True).stdout


def signer(prefix):
    store = os.environ[f"{prefix}_KEYSTORE_FILE"]
    alias = os.environ[f"{prefix}_KEY_ALIAS"]
    for suffix in ("KEYSTORE_PASSWORD", "KEY_PASSWORD"):
        if not os.environ.get(f"{prefix}_{suffix}"):
            raise ValueError(f"Missing {prefix}_{suffix}")
    return [
        "--ks", store,
        "--ks-key-alias", alias,
        "--ks-pass", f"env:{prefix}_KEYSTORE_PASSWORD",
        "--key-pass", f"env:{prefix}_KEY_PASSWORD",
    ]


def verify_for_api(tool, apk, api, expected):
    result = run(*tool, "verify", "--verbose", "--print-certs", "--min-sdk-version", str(api),
                 "--max-sdk-version", str(api), str(apk))
    if api < ROTATION_API:
        certs = re.findall(r"^Signer #1 certificate SHA-256 digest:\s+([a-fA-F0-9]+)$",
                           result, re.MULTILINE)
    else:
        ranges = re.findall(
            r"^Signer \(minSdkVersion=(\d+), maxSdkVersion=(\d+)\) "
            r"certificate SHA-256 digest:\s+([a-fA-F0-9]+)$",
            result, re.MULTILINE,
        )
        certs = [digest for start, end, digest in ranges if int(start) <= api <= int(end)]
    if [cert.lower() for cert in certs] != [expected]:
        raise ValueError(f"Unexpected NÜMA signing certificate on API {api}")
    print(f"Verified NÜMA signature on API {api}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apksigner-jar", type=Path, required=True)
    parser.add_argument("--lineage", type=Path, required=True)
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    if args.input.resolve() == args.output.resolve():
        parser.error("Input and output APK paths must be distinct")
    java_home = os.environ.get("JAVA_HOME")
    java = str(Path(java_home) / "bin" / ("java.exe" if os.name == "nt" else "java")) if java_home else "java"
    tool = [java, "-jar", str(args.apksigner_jar)]
    lineage = run(*tool, "lineage", "--in", str(args.lineage), "--print-certs", "--verbose")
    certificates = re.findall(r"certificate SHA-256 digest: ([a-fA-F0-9]+)", lineage)
    if [digest.lower() for digest in certificates] != [ORIGINAL_CERT, PRODUCTION_CERT]:
        raise ValueError("Signing lineage does not join the published key to the NÜMA production key")
    original_lineage = lineage.split("Signer #2 in lineage certificate DN:", 1)[0]
    capabilities = dict(re.findall(r"^Has (.+?) capability\s*:\s*(true|false)$",
                                   original_lineage, re.MULTILINE))
    # AndroidX redeclares the app's signature-protected receiver permission on update.
    expected_capabilities = dict.fromkeys(("shared UID", "rollback", "auth"), "false")
    expected_capabilities.update({"installed data": "true", "permission": "true"})
    if capabilities != expected_capabilities:
        raise ValueError("The original certificate must retain only installed data and signature permissions")

    original = signer("ANDROID")
    production = signer("ANDROID_ROTATED")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=args.output.parent) as staging:
        candidate = Path(staging) / "numa-signed.apk"
        run(*tool, "sign", "--v4-signing-enabled", "false", "--lineage", str(args.lineage),
            "--rotation-min-sdk-version", str(ROTATION_API), "--out", str(candidate),
            *original, "--next-signer", *production, str(args.input))
        run(*tool, "verify", str(candidate))
        for api in (31, 32):
            verify_for_api(tool, candidate, api, ORIGINAL_CERT)
        for api in (33, 35, 36):
            verify_for_api(tool, candidate, api, PRODUCTION_CERT)
        candidate.replace(args.output)


if __name__ == "__main__":
    main()
