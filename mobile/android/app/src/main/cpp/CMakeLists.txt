# The desktop QuickJS provider host in js/, the Listening Party transport in connect/, and the
# lyric translator in translation/.
# Analysis, Best Mix, the transition planner and rendering come from the Rust
# library (crates/numa-transition-mobile).

cmake_minimum_required(VERSION 3.22.1)
project(numa_native LANGUAGES C CXX)

add_subdirectory(js)
add_subdirectory(connect)
add_subdirectory(discord)
add_subdirectory(translation)
