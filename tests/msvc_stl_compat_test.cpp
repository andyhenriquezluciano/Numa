/* Copyright (C) 2026 SFG545. Licensed under the Numa-Only License (OOL-1.0), see LICENSE-OOL. */
#include "../crates/numa-adaptive-mix/src/msvc_stl_compat.cpp"
#include <cstdio>
#if defined(_MSC_VER) && _MSC_VER == 1944 && defined(_M_X64)
#include <cstdlib>
#include <limits>
#define CHECK(value) do { if (!(value)) { std::fprintf(stderr, "Failed at %d: %s\n", __LINE__, #value); std::abort(); } } while (false)
int main() {
#define EXTREMES(type, minfn, maxfn) { \
    type values[] = {type(3), type(-2), type(3), type(-2)}; \
    CHECK(minfn(values, values + 4) == values + 1); \
    CHECK(maxfn(values, values + 4) == values); \
    CHECK(minfn(values, values) == values); \
    CHECK(maxfn(values, values) == values); \
}
    EXTREMES(int32_t, __std_min_element_4i, __std_max_element_4i)
    EXTREMES(int64_t, __std_min_element_8i, __std_max_element_8i)
    EXTREMES(float, __std_min_element_f_, __std_max_element_f_)
    EXTREMES(double, __std_min_element_d_, __std_max_element_d_)
#undef EXTREMES
    int8_t signedBytes[] = {-128, 127, 127};
    uint8_t unsignedBytes[] = {0, 255, 255};
    CHECK(__std_max_element_1i(signedBytes, signedBytes + 3) == signedBytes + 1);
    CHECK(__std_max_element_1u(unsignedBytes, unsignedBytes + 3) == unsignedBytes + 1);
    int64_t limits[] = {INT64_MIN, 0, INT64_MAX};
    CHECK(__std_is_sorted_until_8i(limits, limits + 3) == limits + 3);
    CHECK(__std_is_sorted_until_8i(limits, limits) == limits);
    int64_t unsorted[] = {3, 4, 2};
    CHECK(__std_is_sorted_until_8i(unsorted, unsorted + 3) == unsorted + 2);
#define MINMAX(type, fn) { \
    type values[] = {5, -7, 5, -7}; \
    auto result = fn(values, values + 4); \
    CHECK(result.min == values + 1 && result.max == values + 2); \
    result = fn(values, values); \
    CHECK(result.min == values && result.max == values); \
}
    MINMAX(int32_t, __std_minmax_element_4i)
    MINMAX(int64_t, __std_minmax_element_8i)
#undef MINMAX
    float floats[] = {0, -std::numeric_limits<float>::infinity(), std::numeric_limits<float>::infinity()};
    CHECK(__std_min_element_f_(floats, floats + 3) == floats + 1);
    CHECK(__std_max_element_f_(floats, floats + 3) == floats + 2);
    int32_t rotated[] = {1, 2, 3, 4};
    __std_rotate(rotated, rotated + 1, rotated + 4);
    CHECK(rotated[0] == 2 && rotated[3] == 1);
    __std_rotate(rotated, rotated, rotated + 4);
    CHECK(rotated[0] == 2 && rotated[3] == 1);
    uint8_t small[] = {1, 1, 2, 2, 1};
    CHECK(__std_unique_1(small, small + 5) == small + 3);
    CHECK(small[0] == 1 && small[1] == 2 && small[2] == 1);
    CHECK(__std_unique_1(small, small) == small);
    uint64_t large[] = {UINT64_MAX, UINT64_MAX, 3, 3};
    CHECK(__std_unique_8(large, large + 4) == large + 2);
    CHECK(large[0] == UINT64_MAX && large[1] == 3);
    CHECK(__std_unique_8(large, large) == large);
    uint64_t haystack[] = {1, 2, 1, 2, 3}, needle[] = {1, 2, 3};
    CHECK(__std_search_8(haystack, haystack + 5, needle, 3) == haystack + 2);
    CHECK(__std_search_8(haystack, haystack + 2, needle, 3) == haystack + 2);
    CHECK(__std_search_8(haystack, haystack + 5, nullptr, 0) == haystack);
    uint16_t words[] = {1, 65535, 1}, copy[3]{};
    __std_replace_copy_2(words, words + 3, copy, 1, 42);
    CHECK(copy[0] == 42 && copy[1] == 65535 && copy[2] == 42);
    CHECK(words[0] == 1);
    CHECK(__std_find_first_not_of_trivial_pos_1("aab", 3, "a", 1) == 2);
    CHECK(__std_find_first_not_of_trivial_pos_1("aab", 3, "ab", 2) == size_t(-1));
    CHECK(__std_find_first_not_of_trivial_pos_1("aab", 3, nullptr, 0) == 0);
    CHECK(__std_find_first_not_of_trivial_pos_1(nullptr, 0, nullptr, 0) == size_t(-1));
    std::puts("All 19 MSVC STL compatibility helpers passed.");
    return 0;
}
#else
int main() { return 77; }
#endif
