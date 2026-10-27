# On-device lyric translation: the desktop's core/native/translation behind JNI.
# libLiteRt.so is Google's prebuilt runtime, packaged from vendor/litert and opened with dlopen.

get_filename_component(NUMA_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../../../.." ABSOLUTE)
set(TRANSLATION_DIR "${NUMA_ROOT}/core/native/translation")

add_library(numa_translation SHARED
    "${TRANSLATION_DIR}/litert_api.cpp"
    "${TRANSLATION_DIR}/lyric_language.cpp"
    "${TRANSLATION_DIR}/marian_model.cpp"
    "${TRANSLATION_DIR}/sentencepiece_tokenizer.cpp"
    "${TRANSLATION_DIR}/translation_pack_table.cpp"
    translation_jni.cpp
)
set_target_properties(numa_translation PROPERTIES CXX_STANDARD 20 CXX_STANDARD_REQUIRED ON)
target_include_directories(numa_translation PRIVATE "${NUMA_ROOT}/core/native" "${NUMA_ROOT}/vendor/litert/include")
# Greedy decode runs a vocab-wide argmax per token; -O0 would make debug builds crawl.
target_compile_options(numa_translation PRIVATE -O2 -fvisibility=hidden)
target_link_libraries(numa_translation PRIVATE ${CMAKE_DL_LIBS})
if(ANDROID)
    target_link_options(numa_translation PRIVATE -Wl,--gc-sections)
endif()
