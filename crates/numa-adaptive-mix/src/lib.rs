/* Copyright (C) 2026 SFG545. Licensed under the Numa-Only License (OOL-1.0), see LICENSE-OOL. */

//! Local adaptive mix preparation. Never runs on the audio callback.
//! Everything outside the `desktop` feature is shared with Android, which links it
//! through `numa-transition-mobile`.
pub mod analysis;
pub mod best_mix;
pub mod beats;
#[cfg(feature = "desktop")]
pub mod decode;
#[cfg(feature = "desktop")]
mod decode_path;
pub mod downmix;
pub mod evidence;
pub mod lock;
pub mod models;
pub mod planner;
pub mod prepare;
pub mod repeat;
pub mod song;
#[cfg(feature = "desktop")]
pub mod text_embed;
