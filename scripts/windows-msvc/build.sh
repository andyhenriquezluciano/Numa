#!/usr/bin/env bash
set -euo pipefail

numa_script=build
source "$(dirname -- "${BASH_SOURCE[0]}")/env.sh"
numa_ensure_host_qt
python3 "$numa_win_dir/check-configured-qt.py" "$NUMA_MSVC_BUILD_DIR" "$NUMA_MSVC_QT_ROOT"
if [[ ! -f "$NUMA_MSVC_BUILD_DIR/meson-private/coredata.dat" ]]; then
  "$numa_win_dir/configure.sh"
fi

export NUMA_MSVC_QT_ROOT NUMA_QT_HOST_PATH
export CARGO_TARGET_X86_64_PC_WINDOWS_MSVC_LINKER="$numa_win_dir/msvc-link"
export CC_x86_64_pc_windows_msvc="$numa_win_dir/msvc-cl"
export CXX_x86_64_pc_windows_msvc="$numa_win_dir/msvc-cl"
export AR_x86_64_pc_windows_msvc="$numa_win_dir/msvc-lib"

mapfile -t bindgen_include_dirs < <("$numa_win_dir/msvc-wine-tool.py" --print-bindgen-includes)
if [[ ${#bindgen_include_dirs[@]} -ne 2 ]] ||
   [[ ! -f "${bindgen_include_dirs[0]}/vcruntime.h" ]] ||
   [[ ! -f "${bindgen_include_dirs[1]}/math.h" ]]; then
  numa_die "MSVC or Windows SDK headers for bindgen are missing"
fi

# %q preserves spaces in paths when bindgen splits these arguments.
printf -v BINDGEN_EXTRA_CLANG_ARGS -- '-isystem %q -isystem %q' \
  "${bindgen_include_dirs[0]}" "${bindgen_include_dirs[1]}"
export BINDGEN_EXTRA_CLANG_ARGS

# Qt 6.11.2 ships the WebEngine backend with the Windows kit.
[[ -f "$NUMA_MSVC_QT_ROOT/plugins/webview/qtwebview_webengine.dll" ]] ||
  numa_die "QtWebView WebEngine plugin missing from $NUMA_MSVC_QT_ROOT"

# Build Numa.
meson compile -C "$NUMA_MSVC_BUILD_DIR" -j "$NUMA_BUILD_JOBS" "$@"
