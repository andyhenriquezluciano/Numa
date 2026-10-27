/*
 * Copyright (C) 2026 SFG545
 *
 * This file is part of Numa.
 *
 * Numa is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * Numa is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Numa. If not, see <https://www.gnu.org/licenses/>.
 */

use biquad::{Biquad, Coefficients, DirectForm1, ToHertz, Type};
use std::slice;

const BANDS: [f32; 10] = [
    31.0, 62.0, 125.0, 250.0, 500.0, 1_000.0, 2_000.0, 4_000.0, 8_000.0, 16_000.0,
];

#[repr(C)]
pub struct NumaAudioEngineConfig {
    pub enabled: u8,
    pub auto_eq_enabled: u8,
    pub eq_enabled: u8,
    pub normalization_enabled: u8,
    pub gains_db: [f32; 10],
    pub preamp_db: f32,
    pub output_gain_db: f32,
    pub q: f32,
    pub balance: f32,
    pub track_gain_db: f32,
}

#[derive(Clone, Copy)]
struct Config {
    enabled: bool,
    auto_eq_enabled: bool,
    eq_enabled: bool,
    normalization_enabled: bool,
    gains_db: [f32; 10],
    preamp_db: f32,
    output_gain_db: f32,
    q: f32,
    balance: f32,
    track_gain_db: f32,
}

impl Default for Config {
    fn default() -> Self {
        Self {
            enabled: true,
            auto_eq_enabled: false,
            eq_enabled: false,
            normalization_enabled: false,
            gains_db: [0.0; 10],
            preamp_db: 0.0,
            output_gain_db: 0.0,
            q: 1.1,
            balance: 0.0,
            track_gain_db: 0.0,
        }
    }
}

impl Config {
    fn from_ffi(value: &NumaAudioEngineConfig) -> Self {
        let mut gains_db = [0.0; 10];
        for (target, source) in gains_db.iter_mut().zip(value.gains_db) {
            *target = finite_clamp(source, -12.0, 12.0, 0.0);
        }
        Self {
            enabled: value.enabled != 0,
            auto_eq_enabled: value.auto_eq_enabled != 0,
            eq_enabled: value.eq_enabled != 0 && value.auto_eq_enabled == 0,
            normalization_enabled: value.normalization_enabled != 0,
            gains_db,
            preamp_db: finite_clamp(value.preamp_db, -12.0, 6.0, 0.0),
            output_gain_db: finite_clamp(value.output_gain_db, -24.0, 6.0, 0.0),
            q: finite_clamp(value.q, 0.4, 2.4, 1.1),
            balance: finite_clamp(value.balance, -1.0, 1.0, 0.0),
            track_gain_db: finite_clamp(value.track_gain_db, -12.0, 12.0, 0.0),
        }
    }
}

fn finite_clamp(value: f32, min: f32, max: f32, fallback: f32) -> f32 {
    if value.is_finite() {
        value.clamp(min, max)
    } else {
        fallback
    }
}

fn db_to_gain(db: f32) -> f32 {
    10.0_f32.powf(db / 20.0)
}

fn peaking_coefficients(
    sample_rate: f32,
    frequency: f32,
    q: f32,
    gain_db: f32,
) -> Coefficients<f32> {
    Coefficients::<f32>::from_params(
        Type::PeakingEQ(gain_db),
        sample_rate.hz(),
        frequency.min(sample_rate * 0.45).hz(),
        q,
    )
    .expect("validated audio engine filter parameters")
}

fn analyzer_coefficients(sample_rate: f32, frequency: f32) -> Coefficients<f32> {
    Coefficients::<f32>::from_params(
        Type::BandPass,
        sample_rate.hz(),
        frequency.min(sample_rate * 0.45).hz(),
        1.1,
    )
    .expect("validated analyzer filter parameters")
}

pub struct NumaAudioEngineHandle {
    engine: AudioEngine,
}

struct AudioEngine {
    sample_rate: f32,
    channels: usize,
    config: Config,
    filters: Vec<Vec<DirectForm1<f32>>>,
    analyzer_filters: Vec<DirectForm1<f32>>,
    analyzer_energy: [f64; 10],
    analyzer_samples: usize,
    meter_energy: [f64; 10],
    meter_samples: usize,
    auto_gains_db: [f32; 10],
    spectrum: [f32; 10],
    compressor_gain: f32,
    smoothed_gain: f32,
    smoothed_balance: f32,
}

impl AudioEngine {
    fn new(sample_rate: u32, channels: usize) -> Option<Self> {
        if !(8_000..=384_000).contains(&sample_rate) || !(1..=8).contains(&channels) {
            return None;
        }
        let sample_rate = sample_rate as f32;
        let config = Config::default();
        let filters = (0..channels)
            .map(|_| {
                BANDS
                    .iter()
                    .map(|frequency| {
                        DirectForm1::new(peaking_coefficients(
                            sample_rate,
                            *frequency,
                            config.q,
                            0.0,
                        ))
                    })
                    .collect()
            })
            .collect();
        let analyzer_filters = BANDS
            .iter()
            .map(|frequency| DirectForm1::new(analyzer_coefficients(sample_rate, *frequency)))
            .collect();
        Some(Self {
            sample_rate,
            channels,
            config,
            filters,
            analyzer_filters,
            analyzer_energy: [0.0; 10],
            analyzer_samples: 0,
            meter_energy: [0.0; 10],
            meter_samples: 0,
            auto_gains_db: [0.0; 10],
            spectrum: [0.0; 10],
            compressor_gain: 1.0,
            smoothed_gain: 1.0,
            smoothed_balance: 0.0,
        })
    }

    fn configure(&mut self, config: Config) {
        self.config = config;
        self.rebuild_filters();
    }

    fn rebuild_filters(&mut self) {
        let gains = if self.config.auto_eq_enabled {
            self.auto_gains_db
        } else {
            self.config.gains_db
        };
        for channel in &mut self.filters {
            for (index, filter) in channel.iter_mut().enumerate() {
                filter.update_coefficients(peaking_coefficients(
                    self.sample_rate,
                    BANDS[index],
                    self.config.q,
                    gains[index],
                ));
            }
        }
    }

    fn reset(&mut self) {
        for channel in &mut self.filters {
            for filter in channel {
                filter.reset_state();
            }
        }
        for filter in &mut self.analyzer_filters {
            filter.reset_state();
        }
        self.analyzer_energy = [0.0; 10];
        self.analyzer_samples = 0;
        self.meter_energy = [0.0; 10];
        self.meter_samples = 0;
        self.auto_gains_db = [0.0; 10];
        self.spectrum = [0.0; 10];

        self.smoothed_balance = self.config.balance;
        self.rebuild_filters();
    }

    fn process(&mut self, samples: &mut [f32]) -> bool {
        if !samples.len().is_multiple_of(self.channels) {
            return false;
        }

        let use_eq = self.config.enabled && (self.config.eq_enabled || self.config.auto_eq_enabled);
        let preamp = if self.config.enabled && self.config.eq_enabled {
            db_to_gain(self.config.preamp_db)
        } else {
            1.0
        };
        let target_gain = db_to_gain(
            self.config.output_gain_db
                + if self.config.enabled {
                    self.config.track_gain_db
                } else {
                    0.0
                },
        );
        let smooth = (-1.0 / (0.012 * self.sample_rate)).exp();

        for frame in samples.chunks_exact_mut(self.channels) {
            let mono = frame
                .iter()
                .map(|sample| if sample.is_finite() { *sample } else { 0.0 })
                .sum::<f32>()
                / self.channels as f32;
            for (index, filter) in self.analyzer_filters.iter_mut().enumerate() {
                let band = filter.run(mono);
                let energy = f64::from(band) * f64::from(band);
                self.analyzer_energy[index] += energy;
                self.meter_energy[index] += energy;
            }
            self.analyzer_samples += 1;
            self.meter_samples += 1;

            for (channel_index, sample) in frame.iter_mut().enumerate() {
                let mut value = if sample.is_finite() { *sample } else { 0.0 };
                if use_eq {
                    value *= preamp;
                    for filter in &mut self.filters[channel_index] {
                        value = filter.run(value);
                    }
                }
                *sample = value;
            }

            self.smoothed_balance =
                self.config.balance + smooth * (self.smoothed_balance - self.config.balance);
            if self.channels >= 2 && self.config.enabled {
                if self.smoothed_balance > 0.0 {
                    frame[0] *= 1.0 - self.smoothed_balance;
                } else if self.smoothed_balance < 0.0 {
                    frame[1] *= 1.0 + self.smoothed_balance;
                }
            }

            self.smoothed_gain = target_gain + smooth * (self.smoothed_gain - target_gain);
            for sample in frame.iter_mut() {
                *sample *= self.smoothed_gain;
            }

            if self.config.enabled && self.config.normalization_enabled {
                let peak = frame
                    .iter()
                    .fold(0.0_f32, |value, sample| value.max(sample.abs()));
                let target = compressor_target_gain(peak);
                let seconds = if target < self.compressor_gain {
                    0.02
                } else {
                    0.3
                };
                let coefficient = (-1.0 / (seconds * self.sample_rate)).exp();
                self.compressor_gain = target + coefficient * (self.compressor_gain - target);
                for sample in frame.iter_mut() {
                    *sample *= self.compressor_gain;
                }
            } else {
                self.compressor_gain = 1.0;
            }

            for sample in frame.iter_mut() {
                *sample = finite_clamp(*sample, -1.0, 1.0, 0.0);
            }
        }

        // Spectrum metering is intentionally much faster than Automatic EQ.
        if self.meter_samples >= (self.sample_rate * 0.05) as usize {
            self.update_spectrum();
        }
        if self.analyzer_samples >= (self.sample_rate * 0.75) as usize {
            self.update_auto_eq_analysis();
        }
        true
    }

    fn update_spectrum(&mut self) {
        if self.meter_samples == 0 {
            return;
        }
        for index in 0..10 {
            let rms = (self.meter_energy[index] / self.meter_samples as f64).sqrt() as f32;
            let level_db = 20.0 * rms.max(1.0e-6).log10();
            let target = ((level_db + 60.0) / 60.0).clamp(0.0, 1.0);
            self.spectrum[index] = self.spectrum[index] * 0.35 + target * 0.65;
            self.meter_energy[index] = 0.0;
        }
        self.meter_samples = 0;
    }

    fn update_auto_eq_analysis(&mut self) {
        if self.analyzer_samples == 0 {
            return;
        }
        let mut band_db = [0.0_f32; 10];
        let mut audible = false;
        for (index, value) in band_db.iter_mut().enumerate() {
            let rms = (self.analyzer_energy[index] / self.analyzer_samples as f64).sqrt() as f32;
            audible |= rms > 1.0e-5;
            *value = 20.0 * rms.max(1.0e-6).log10();
            self.analyzer_energy[index] = 0.0;
        }
        self.analyzer_samples = 0;

        if !self.config.auto_eq_enabled || !audible {
            return;
        }
        let mean = band_db.iter().sum::<f32>() / band_db.len() as f32;
        let tilt = [0.4, 0.35, 0.25, 0.1, 0.0, 0.0, -0.1, -0.2, -0.3, -0.35];
        for ((gain, level), target_tilt) in self.auto_gains_db.iter_mut().zip(band_db).zip(tilt) {
            let correction = ((mean + target_tilt - level) * 0.18).clamp(-3.0, 3.0);
            *gain = (*gain * 0.74 + correction * 0.26).clamp(-3.0, 3.0);
        }
        self.rebuild_filters();
    }
}

fn compressor_target_gain(peak: f32) -> f32 {
    if peak <= 0.0 {
        return 1.0;
    }
    let level_db = 20.0 * peak.log10();
    let threshold = -24.0;
    let knee = 18.0;
    let ratio = 4.0;
    let lower = threshold - knee * 0.5;
    let upper = threshold + knee * 0.5;
    let over = if level_db <= lower {
        0.0
    } else if level_db >= upper {
        level_db - threshold
    } else {
        let x = level_db - lower;
        x * x / (2.0 * knee)
    };
    db_to_gain(-over * (1.0 - 1.0 / ratio))
}

mod ffi;
pub use ffi::*;

#[cfg(test)]
mod tests;
