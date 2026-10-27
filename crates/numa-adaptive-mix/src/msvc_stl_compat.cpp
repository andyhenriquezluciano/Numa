/* Copyright (C) 2026 SFG545. Licensed under the Numa-Only License (OOL-1.0), see LICENSE-OOL. */

// ONNX's precompiled objects use STL helpers absent from the 14.44 import library.
// ABI declarations: microsoft/STL stl/inc/{xutility,algorithm,__msvc_string_view.hpp}.
#if defined(_MSC_VER) && _MSC_VER == 1944 && defined(_M_X64)
#define _USE_STD_VECTOR_ALGORITHMS 0
#include <algorithm>
#include <cstddef>
#include <cstdint>

#define NUMA_EXTREME(name, algorithm, type) \
extern "C" const void* __stdcall name(const void* first, const void* last) noexcept { \
    return std::algorithm(static_cast<const type*>(first), static_cast<const type*>(last)); \
}
NUMA_EXTREME(__std_max_element_1i, max_element, int8_t)
NUMA_EXTREME(__std_max_element_1u, max_element, uint8_t)
NUMA_EXTREME(__std_max_element_4i, max_element, int32_t)
NUMA_EXTREME(__std_max_element_8i, max_element, int64_t)
NUMA_EXTREME(__std_max_element_f_, max_element, float)
NUMA_EXTREME(__std_max_element_d_, max_element, double)
NUMA_EXTREME(__std_min_element_4i, min_element, int32_t)
NUMA_EXTREME(__std_min_element_8i, min_element, int64_t)
NUMA_EXTREME(__std_min_element_f_, min_element, float)
NUMA_EXTREME(__std_min_element_d_, min_element, double)
NUMA_EXTREME(__std_is_sorted_until_8i, is_sorted_until, int64_t)
#undef NUMA_EXTREME

struct NumaMinMax { const void* min; const void* max; };
#define NUMA_MINMAX(name, type) \
extern "C" NumaMinMax __stdcall name(const void* first, const void* last) noexcept { \
    const auto pair = std::minmax_element(static_cast<const type*>(first), static_cast<const type*>(last)); \
    return {pair.first, pair.second}; \
}
NUMA_MINMAX(__std_minmax_element_4i, int32_t)
NUMA_MINMAX(__std_minmax_element_8i, int64_t)
#undef NUMA_MINMAX

extern "C" void __stdcall __std_rotate(void* first, void* middle, void* last) noexcept {
    std::rotate(static_cast<uint8_t*>(first), static_cast<uint8_t*>(middle), static_cast<uint8_t*>(last));
}
extern "C" void* __stdcall __std_unique_1(void* first, void* last) noexcept {
    return std::unique(static_cast<uint8_t*>(first), static_cast<uint8_t*>(last));
}
extern "C" void* __stdcall __std_unique_8(void* first, void* last) noexcept {
    return std::unique(static_cast<uint64_t*>(first), static_cast<uint64_t*>(last));
}
extern "C" const void* __stdcall __std_search_8(
    const void* first, const void* last, const void* needle, size_t count) noexcept {
    if (!count) return first;
    const auto* pattern = static_cast<const uint64_t*>(needle);
    return std::search(static_cast<const uint64_t*>(first), static_cast<const uint64_t*>(last), pattern, pattern + count);
}
extern "C" void __stdcall __std_replace_copy_2(
    const void* first, const void* last, void* output, uint16_t oldValue, uint16_t newValue) noexcept {
    std::replace_copy(static_cast<const uint16_t*>(first), static_cast<const uint16_t*>(last),
                      static_cast<uint16_t*>(output), oldValue, newValue);
}
extern "C" size_t __stdcall __std_find_first_not_of_trivial_pos_1(
    const void* haystack, size_t length, const void* needle, size_t count) noexcept {
    const auto* bytes = static_cast<const uint8_t*>(haystack);
    const auto* excluded = static_cast<const uint8_t*>(needle);
    if (!count) return length ? 0 : size_t(-1);
    for (size_t i = 0; i < length; ++i)
        if (std::find(excluded, excluded + count, bytes[i]) == excluded + count) return i;
    return size_t(-1);
}
#endif
