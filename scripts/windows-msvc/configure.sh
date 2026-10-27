#!/usr/bin/env bash
set -euo pipefail
numa_script=configure
source "$(dirname -- "${BASH_SOURCE[0]}")/env.sh"
numa_ensure_host_qt

[[ -x "$NUMA_HOST_QJSC" ]] || numa_die "host qjsc missing; run scripts/windows-msvc/setup.sh"
[[ -d "$NUMA_MSVC_QT_ROOT" ]] || numa_die "Windows Qt missing: $NUMA_MSVC_QT_ROOT (run setup.sh)"
python3 "$numa_win_dir/check-configured-qt.py" "$NUMA_MSVC_BUILD_DIR" "$NUMA_MSVC_QT_ROOT"
export NUMA_MSVC_QT_ROOT NUMA_QT_HOST_PATH

numa_need_cmd meson "Meson 1.12 or newer"
cross_file="$NUMA_WIN_TOOLS/meson-msvc-$NUMA_QT_VERSION-cross.ini"
python3 "$numa_win_dir/meson-cross.py" \
  --script-dir "$numa_win_dir" --qt-root "$NUMA_MSVC_QT_ROOT" \
  --host-qt-root "$NUMA_QT_HOST_PATH" \
  --output "$cross_file"
meson_setup_args=()
if [[ -f "$NUMA_MSVC_BUILD_DIR/meson-info/meson-info.json" ]]; then
  meson_setup_args+=(--reconfigure)
fi
meson setup "${meson_setup_args[@]}" "$NUMA_MSVC_BUILD_DIR" "$numa_source_dir" \
  --cross-file "$cross_file" --buildtype release \
  -Dnuma_rust_target=x86_64-pc-windows-msvc \
  -Dtests=false -Dhost_qjsc="$NUMA_HOST_QJSC" \
  -Dhost_rcc="$(command -v rcc)" -Dhost_qsb="$(command -v qsb)" "$@"
