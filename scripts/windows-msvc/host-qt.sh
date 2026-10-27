# Matching Linux tools for the Windows Qt kit. Sourced by env.sh.

numa_host_qt_version() {
    local config="$1/lib/cmake/Qt6Core/Qt6CoreConfigVersionImpl.cmake"
    [[ -f "$config" ]] || return 0
    sed -n 's/^set(PACKAGE_VERSION "\([0-9][0-9.]*\)")/\1/p' "$config" | head -n 1
}

numa_aqt() {
    if command -v uvx >/dev/null 2>&1; then
        uvx --from "$NUMA_AQT_SPEC" aqt "$@"
    elif command -v pipx >/dev/null 2>&1; then
        pipx run --spec "$NUMA_AQT_SPEC" aqt "$@"
    else
        numa_die "uv or pipx is required to download Qt"
    fi
}

numa_host_qt_explicit="${NUMA_QT_HOST_PATH:+1}"
if [[ -z "${NUMA_QT_HOST_PATH:-}" ]]; then
    if [[ "$(numa_host_qt_version /usr)" == "$NUMA_QT_VERSION" ]]; then
        NUMA_QT_HOST_PATH=/usr
    else
        NUMA_QT_HOST_PATH="$NUMA_WIN_TOOLS/host-qt/$NUMA_QT_VERSION/gcc_64"
    fi
fi

numa_ensure_host_qt() {
    local version
    version="$(numa_host_qt_version "$NUMA_QT_HOST_PATH")"
    if [[ -z "$version" && -z "$numa_host_qt_explicit" ]]; then
        mkdir -p "$NUMA_WIN_TOOLS"
        echo "Installing Linux Qt $NUMA_QT_VERSION tools for the Windows build"
        (cd "$NUMA_WIN_TOOLS" && numa_aqt install-qt linux desktop \
            "$NUMA_QT_VERSION" linux_gcc_64 -m qtshadertools \
            --archives qtbase qtdeclarative icu -O "$NUMA_WIN_TOOLS/host-qt")
        version="$(numa_host_qt_version "$NUMA_QT_HOST_PATH")"
    fi
    [[ "$version" == "$NUMA_QT_VERSION" ]] ||
        numa_die "host Qt ${version:-missing} at $NUMA_QT_HOST_PATH differs from target Qt $NUMA_QT_VERSION; set NUMA_QT_HOST_PATH to a matching host SDK"
    export NUMA_QT_HOST_PATH
    export PATH="$NUMA_QT_HOST_PATH/libexec:$NUMA_QT_HOST_PATH/bin:$NUMA_QT_HOST_PATH/lib/qt6:$NUMA_QT_HOST_PATH/lib/qt6/bin:$PATH"
    export PKG_CONFIG_PATH="$NUMA_QT_HOST_PATH/lib/pkgconfig${PKG_CONFIG_PATH:+:$PKG_CONFIG_PATH}"
}
