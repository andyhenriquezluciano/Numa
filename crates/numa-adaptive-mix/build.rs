/* Copyright (C) 2026 SFG545. Licensed under the Numa-Only License (OOL-1.0), see LICENSE-OOL. */

// ORT copies onnxruntime and Dawn beside the worker; load them from there.
fn main() {
    println!("cargo:rerun-if-changed=src/msvc_stl_compat.cpp");
    if std::env::var("CARGO_CFG_TARGET_OS").as_deref() == Ok("windows")
        && std::env::var("CARGO_CFG_TARGET_ENV").as_deref() == Ok("msvc")
        && std::env::var("CARGO_CFG_TARGET_ARCH").as_deref() == Ok("x86_64")
    {
        cc::Build::new().cpp(true).std("c++17")
            .file("src/msvc_stl_compat.cpp").compile("numa_msvc_stl_compat");
    }
    if std::env::var("CARGO_CFG_TARGET_OS").as_deref() == Ok("linux") {
        println!("cargo:rustc-link-arg=-Wl,-rpath,$ORIGIN");
    }
}
