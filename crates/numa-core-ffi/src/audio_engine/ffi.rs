/* Copyright (C) 2026 SFG545. SPDX-License-Identifier: AGPL-3.0-or-later */

use super::*;

#[unsafe(no_mangle)]
pub extern "C" fn numa_audio_engine_create(
    sample_rate: u32,
    channels: u32,
) -> *mut NumaAudioEngineHandle {
    std::panic::catch_unwind(|| {
        AudioEngine::new(sample_rate, channels as usize)
            .map(|engine| Box::into_raw(Box::new(NumaAudioEngineHandle { engine })))
            .unwrap_or(std::ptr::null_mut())
    })
    .unwrap_or(std::ptr::null_mut())
}

#[unsafe(no_mangle)]
/// Applies a complete configuration to a live audio engine.
///
/// # Safety
/// `handle` must be a live handle returned by [`numa_audio_engine_create`],
/// and `config` must point to a readable configuration for the duration of the call.
pub unsafe extern "C" fn numa_audio_engine_configure(
    handle: *mut NumaAudioEngineHandle,
    config: *const NumaAudioEngineConfig,
) -> u8 {
    if handle.is_null() || config.is_null() {
        return 0;
    }
    std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let handle = unsafe { &mut *handle };
        let config = unsafe { &*config };
        handle.engine.configure(Config::from_ffi(config));
        1
    }))
    .unwrap_or(0)
}

#[unsafe(no_mangle)]
/// Processes interleaved floating-point PCM in place.
///
/// # Safety
/// `handle` must be live and `samples` must point to at least
/// `frame_count * channels` writable `f32` values for the engine's channel count.
pub unsafe extern "C" fn numa_audio_engine_process(
    handle: *mut NumaAudioEngineHandle,
    samples: *mut f32,
    frame_count: usize,
) -> u8 {
    if handle.is_null() || samples.is_null() || frame_count == 0 {
        return 0;
    }
    std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let handle = unsafe { &mut *handle };
        let sample_count = match frame_count.checked_mul(handle.engine.channels) {
            Some(value) => value,
            None => return 0,
        };
        let samples = unsafe { slice::from_raw_parts_mut(samples, sample_count) };
        u8::from(handle.engine.process(samples))
    }))
    .unwrap_or(0)
}

#[unsafe(no_mangle)]
/// Clears filter and analyzer history while preserving gain envelopes and configuration.
///
/// # Safety
/// `handle` must be null or a live handle returned by [`numa_audio_engine_create`].
pub unsafe extern "C" fn numa_audio_engine_reset(handle: *mut NumaAudioEngineHandle) {
    if handle.is_null() {
        return;
    }
    let _ = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        unsafe { &mut *handle }.engine.reset();
    }));
}

#[unsafe(no_mangle)]
/// Copies the ten current automatic-EQ gains into caller-owned storage.
///
/// # Safety
/// `handle` must be live and `gains` must point to ten writable `f32` values.
pub unsafe extern "C" fn numa_audio_engine_auto_gains(
    handle: *const NumaAudioEngineHandle,
    gains: *mut f32,
) -> u8 {
    if handle.is_null() || gains.is_null() {
        return 0;
    }
    let handle = unsafe { &*handle };
    unsafe { std::ptr::copy_nonoverlapping(handle.engine.auto_gains_db.as_ptr(), gains, 10) };
    1
}

#[unsafe(no_mangle)]
/// Copies the ten current normalized spectrum levels into caller-owned storage.
///
/// # Safety
/// `handle` must be live and `levels` must point to ten writable `f32` values.
pub unsafe extern "C" fn numa_audio_engine_spectrum(
    handle: *const NumaAudioEngineHandle,
    levels: *mut f32,
) -> u8 {
    if handle.is_null() || levels.is_null() {
        return 0;
    }
    let handle = unsafe { &*handle };
    unsafe { std::ptr::copy_nonoverlapping(handle.engine.spectrum.as_ptr(), levels, 10) };
    1
}

#[unsafe(no_mangle)]
/// Releases an audio engine handle.
///
/// # Safety
/// `handle` must be null or a live handle returned by [`numa_audio_engine_create`]
/// that has not already been destroyed.
pub unsafe extern "C" fn numa_audio_engine_destroy(handle: *mut NumaAudioEngineHandle) {
    if !handle.is_null() {
        drop(unsafe { Box::from_raw(handle) });
    }
}

