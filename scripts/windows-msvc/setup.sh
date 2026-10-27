#!/usr/bin/env bash
# Fetch and prepare everything the Windows/MSVC cross build needs, without touching system state.
# Usage: setup.sh [--accept-msvc-license] [wine] [msvc] [qt] [host-qt] [qjsc]
set -euo pipefail
numa_script=setup
source "$(dirname -- "${BASH_SOURCE[0]}")/env.sh"

steps=()
for arg in "$@"; do
  case "$arg" in
    --accept-msvc-license) NUMA_ACCEPT_MSVC_LICENSE=1 ;;
    wine|msvc|qt|host-qt|qjsc) steps+=("$arg") ;;
    *) numa_die "unknown argument: $arg" ;;
  esac
done
[[ ${#steps[@]} -gt 0 ]] || steps=(wine msvc qt qjsc)

numa_need_cmd cmake "cmake"
numa_need_cmd meson "Meson 1.12 or newer"
numa_need_cmd ninja "ninja"
numa_need_cmd wine "needed to run cl.exe, link.exe and windeployqt"
numa_need_cmd python3 "python 3"
numa_need_cmd cargo "rustup"
numa_need_cmd 7z "p7zip, used for packaging"
numa_need_cmd git "git, used to fetch aqtinstall"
rustup target list --installed 2>/dev/null | grep -qx x86_64-pc-windows-msvc ||
  numa_die "missing Rust target; run: rustup target add x86_64-pc-windows-msvc"

mkdir -p "$NUMA_WIN_TOOLS"

setup_wine() {
  [[ -f "$WINEPREFIX/system.reg" ]] && return
  echo "Creating Wine prefix at $WINEPREFIX"
  WINEARCH=win64 wineboot --init >/dev/null 2>&1
}

setup_msvc() {
  if [[ -f "$NUMA_MSVC_VC_ROOT/Auxiliary/Build/vcvarsall.bat" || -d "$NUMA_MSVC_VC_ROOT/Tools/MSVC" ]]; then
    echo "MSVC already present: $NUMA_MSVC_VC_ROOT"
    return
  fi
  [[ "${NUMA_ACCEPT_MSVC_LICENSE:-0}" == 1 ]] ||
    numa_die "MSVC Build Tools need license acceptance (https://go.microsoft.com/fwlink/?LinkId=2179911); rerun with --accept-msvc-license"
  numa_need_cmd curl "curl"
  numa_need_cmd msiextract "msitools, used to unpack the Windows SDK"
  # github.com tarball: raw.githubusercontent.com is blocked on some networks.
  local msvc_wine="$NUMA_WIN_TOOLS/msvc-wine-$NUMA_MSVC_WINE_REV"
  if [[ ! -f "$msvc_wine/vsdownload.py" ]]; then
    mkdir -p "$msvc_wine"
    curl -fsSL "https://github.com/mstorsjo/msvc-wine/archive/$NUMA_MSVC_WINE_REV.tar.gz" |
      tar -xz --strip-components=1 -C "$msvc_wine"
  fi
  python3 "$msvc_wine/vsdownload.py" --accept-license --dest "$NUMA_MSVC_ROOT" \
    --cache "$NUMA_WIN_TOOLS/downloads" --major "$NUMA_VS_MAJOR" \
    ${NUMA_MSVC_VS_VERSION:+--msvc-version "$NUMA_MSVC_VS_VERSION"} \
    --sdk-version "$NUMA_WINSDK_BUILD" --architecture x64
}

setup_qt() {
  if [[ -f "$NUMA_MSVC_QT_ROOT/lib/cmake/Qt6/Qt6Config.cmake" ]]; then
    [[ -f "$NUMA_MSVC_QT_ROOT/bin/Qt6SerialPort.dll" ]] ||
      numa_die "Qt Serial Port missing; install qtserialport into $NUMA_MSVC_QT_ROOT"
    [[ -f "$NUMA_MSVC_QT_ROOT/plugins/webview/qtwebview_webengine.dll" ]] ||
      numa_die "QtWebView WebEngine plugin missing from $NUMA_MSVC_QT_ROOT"
    echo "Qt already present: $NUMA_MSVC_QT_ROOT"
    return
  fi
  # aqt writes <out>/<version>/msvc2022_64.
  # shellcheck disable=SC2086
  # aqt writes aqtinstall.log into the working directory.
  (cd "$NUMA_WIN_TOOLS" && numa_aqt install-qt windows desktop "$NUMA_QT_VERSION" "$NUMA_QT_ARCH" \
    -m $NUMA_QT_MODULES -O "$NUMA_WIN_TOOLS/qt")
}

setup_qjsc() {
  [[ -x "$NUMA_HOST_QJSC" ]] && return
  local dir
  dir="$(dirname -- "$NUMA_HOST_QJSC")"
  cmake -S "$numa_source_dir/third_party/quickjs" -B "$dir" -G Ninja -DCMAKE_BUILD_TYPE=Release
  cmake --build "$dir" --target qjsc
  [[ -x "$NUMA_HOST_QJSC" ]] || numa_die "qjsc was not produced at $NUMA_HOST_QJSC"
}

for step in "${steps[@]}"; do
  if [[ "$step" == host-qt ]]; then
    numa_ensure_host_qt
  else
    "setup_$step"
    [[ "$step" != qt ]] || numa_ensure_host_qt
  fi
done
echo "Windows toolchain ready under $NUMA_WIN_TOOLS"
