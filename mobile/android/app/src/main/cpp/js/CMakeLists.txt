# Desktop's QuickJS and provider host, built for Android and for
# host unit tests. Configured on its own, this also builds the host qjsc.

cmake_minimum_required(VERSION 3.22.1)
project(numa_js LANGUAGES C CXX)

get_filename_component(NUMA_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../../../.." ABSOLUTE)
set(QUICKJS_DIR "${NUMA_ROOT}/third_party/quickjs")

add_library(numa_js SHARED
    "${QUICKJS_DIR}/quickjs.c"
    "${QUICKJS_DIR}/dtoa.c"
    "${QUICKJS_DIR}/libregexp.c"
    "${QUICKJS_DIR}/libunicode.c"
    "${NUMA_ROOT}/core/native/provider_host/provider_host.cpp"
    "${NUMA_ROOT}/core/native/provider_host/provider_host_media.cpp"
    provider_jni.cpp
)
set_target_properties(numa_js PROPERTIES C_STANDARD 11 CXX_STANDARD 17 CXX_STANDARD_REQUIRED ON)
target_include_directories(numa_js PRIVATE "${QUICKJS_DIR}" "${NUMA_ROOT}/core/native")
target_compile_definitions(numa_js PRIVATE _GNU_SOURCE QUICKJS_NG_BUILD)
# An -O0 interpreter makes every debug-build transition feel like vinyl at 16 rpm.
if(MSVC)
    target_compile_options(numa_js PRIVATE /O2 /utf-8 $<$<COMPILE_LANGUAGE:C>:/J /w>)
else()
    target_compile_options(numa_js PRIVATE -O2 -fvisibility=hidden
        $<$<COMPILE_LANGUAGE:C>:-funsigned-char -w>)
    target_link_libraries(numa_js PRIVATE m ${CMAKE_DL_LIBS})
endif()

if(ANDROID)
    target_link_libraries(numa_js PRIVATE log)
else()
    find_package(JNI REQUIRED)
    target_include_directories(numa_js PRIVATE ${JNI_INCLUDE_DIRS})
    if(NOT WIN32)
        target_link_libraries(numa_js PRIVATE pthread)
    endif()
    # Provider bytecode must come from this exact engine, so build its compiler too.
    set(QJS_BUILD_LIBC OFF CACHE BOOL "" FORCE)
    add_subdirectory("${QUICKJS_DIR}" quickjs EXCLUDE_FROM_ALL)
endif()
