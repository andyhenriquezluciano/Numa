# Shared settings for the Windows/MSVC cross build. Source, do not execute.
# Every path can be overridden from the environment.

numa_win_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
numa_source_dir="$(cd -- "$numa_win_dir/../.." && pwd)"

: "${NUMA_QT_VERSION:=6.11.2}"

# Other pinned inputs (override only to test a new toolchain).
: "${NUMA_QT_ARCH:=win64_msvc2022_64}"
: "${NUMA_QT_MODULES:=qtmultimedia qtshadertools qtwebengine qtwebview qtwebchannel qtpositioning qtserialport qtwebsockets}"
# Released aqtinstall lacks the Qt 6.11 repository layout; pin a git commit.
: "${NUMA_AQT_SPEC:=git+https://github.com/miurahr/aqtinstall@076e1659807d0b362a3ed684d54c2e9c775eb9c7}"
: "${NUMA_MSVC_WINE_REV:=514f8ea34842cd6d831804d0e9658d3a32870ae1}"
# Empty selects the newest toolset in the VS channel (14.51+, needed by the prebuilt ONNX Runtime).
: "${NUMA_MSVC_VS_VERSION:=}"
: "${NUMA_VS_MAJOR:=18}"
: "${NUMA_WINSDK_BUILD:=10.0.26100}"

# Downloaded toolchains live here; the directory is git-ignored (/.cache/).
: "${NUMA_WIN_TOOLS:=$numa_source_dir/.cache/windows-msvc}"
: "${NUMA_MSVC_BUILD_DIR:=$numa_source_dir/build-windows-msvc-$NUMA_QT_VERSION}"
: "${NUMA_MSVC_ROOT:=$NUMA_WIN_TOOLS/msvc}"
: "${NUMA_MSVC_VC_ROOT:=$NUMA_MSVC_ROOT/VC}"
: "${NUMA_WINSDK_ROOT:=$NUMA_MSVC_ROOT/Windows Kits/10}"
: "${NUMA_MSVC_QT_ROOT:=$NUMA_WIN_TOOLS/qt/$NUMA_QT_VERSION/msvc2022_64}"
: "${WINEPREFIX:=$NUMA_WIN_TOOLS/wine-prefix}"
: "${NUMA_HOST_QJSC:=$NUMA_WIN_TOOLS/host-qjsc/qjsc}"
: "${NUMA_BUILD_JOBS:=$(nproc)}"

export NUMA_MSVC_VC_ROOT NUMA_WINSDK_ROOT WINEPREFIX
export WINEDEBUG="${WINEDEBUG:--all}"

numa_die() {
    echo "${numa_script:-windows-msvc}: $*" >&2
    exit 1
}

numa_need_cmd() {
    command -v "$1" >/dev/null 2>&1 || numa_die "missing host tool: $1 ($2)"
}

source "$numa_win_dir/host-qt.sh"
