# Listening Party WebRTC bindings use the shared native transport core.

get_filename_component(NUMA_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../../../.." ABSOLUTE)
add_subdirectory("${NUMA_ROOT}/core/native/connect" connect_core)

add_library(numa_connect_jni SHARED party_jni.cpp)
set_target_properties(numa_connect_jni PROPERTIES CXX_STANDARD 17 CXX_STANDARD_REQUIRED ON)
target_compile_options(numa_connect_jni PRIVATE -fvisibility=hidden)
target_link_libraries(numa_connect_jni PRIVATE numa_connect)
if(ANDROID)
    target_link_libraries(numa_connect_jni PRIVATE log)
    # Every dependency is static; keep only what Connect reaches.
    target_link_options(numa_connect_jni PRIVATE -Wl,--gc-sections -Wl,--exclude-libs,ALL)
endif()
