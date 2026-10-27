set(CMAKE_SYSTEM_NAME Windows)
set(CMAKE_SYSTEM_PROCESSOR AMD64)

get_filename_component(NUMA_MSVC_WRAPPER_DIR "${CMAKE_CURRENT_LIST_DIR}" ABSOLUTE)

# Paths come from env.sh via configure.sh; fail early when the toolchain is absent.
foreach(var NUMA_MSVC_QT_ROOT NUMA_QT_HOST_PATH)
    if(NOT DEFINED ENV{${var}})
        message(FATAL_ERROR "${var} is not set; run scripts/windows-msvc/configure.sh")
    endif()
endforeach()

set(CMAKE_C_COMPILER "${NUMA_MSVC_WRAPPER_DIR}/msvc-cl")
set(CMAKE_CXX_COMPILER "${NUMA_MSVC_WRAPPER_DIR}/msvc-cl")
set(CMAKE_LINKER "${NUMA_MSVC_WRAPPER_DIR}/msvc-link")
set(CMAKE_AR "${NUMA_MSVC_WRAPPER_DIR}/msvc-lib")
set(CMAKE_RC_COMPILER "${NUMA_MSVC_WRAPPER_DIR}/msvc-rc")
set(CMAKE_MT "${NUMA_MSVC_WRAPPER_DIR}/msvc-mt")

set(CMAKE_PREFIX_PATH "$ENV{NUMA_MSVC_QT_ROOT}")
set(CMAKE_FIND_ROOT_PATH "$ENV{NUMA_MSVC_QT_ROOT}")
set(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)
set(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY ONLY)
set(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE ONLY)
set(CMAKE_FIND_ROOT_PATH_MODE_PACKAGE ONLY)

# Host Qt tools (moc, rcc, qsb) must match the target Qt minor version.
set(QT_HOST_PATH "$ENV{NUMA_QT_HOST_PATH}")
set(QT_HOST_PATH_CMAKE_DIR "$ENV{NUMA_QT_HOST_PATH}/lib/cmake")
