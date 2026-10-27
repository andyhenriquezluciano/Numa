/* Copyright (C) 2026 SFG545. SPDX-License-Identifier: AGPL-3.0-or-later */

use super::*;

    #[test]
    fn flat_engine_keeps_signal_close_to_input() {
        let mut engine = AudioEngine::new(48_000, 2).unwrap();
        let mut samples = (0..480).flat_map(|_| [0.25_f32, -0.25]).collect::<Vec<_>>();
        let original = samples.clone();
        assert!(engine.process(&mut samples));
        for (actual, expected) in samples.iter().zip(original) {
            assert!((actual - expected).abs() < 1.0e-5);
        }
    }

    #[test]
    fn global_gain_applies_while_engine_is_bypassed() {
        let mut engine = AudioEngine::new(48_000, 2).unwrap();
        engine.configure(Config {
            enabled: false,
            output_gain_db: -6.0,
            ..Config::default()
        });
        let mut samples = vec![0.5_f32; 4_800];
        assert!(engine.process(&mut samples));
        assert!(samples.last().unwrap() < &0.27);
        assert!(samples.last().unwrap() > &0.23);
    }

    #[test]
    fn manual_eq_changes_filtered_audio() {
        let mut engine = AudioEngine::new(48_000, 2).unwrap();
        let mut gains = [0.0; 10];
        gains[5] = 12.0;
        engine.configure(Config {
            eq_enabled: true,
            gains_db: gains,
            ..Config::default()
        });
        let mut samples = Vec::with_capacity(9_600);
        for frame in 0..4_800 {
            let sample =
                (2.0 * std::f32::consts::PI * 1_000.0 * frame as f32 / 48_000.0).sin() * 0.1;
            samples.extend([sample, sample]);
        }
        assert!(engine.process(&mut samples));
        let tail_peak = samples[samples.len() - 960..]
            .iter()
            .fold(0.0_f32, |peak, sample| peak.max(sample.abs()));
        assert!(tail_peak > 0.2);
    }

    #[test]
    fn automatic_eq_produces_bounded_adjustments() {
        let mut engine = AudioEngine::new(48_000, 2).unwrap();
        engine.configure(Config {
            auto_eq_enabled: true,
            ..Config::default()
        });
        let mut samples = Vec::with_capacity(96_000);
        for frame in 0..48_000 {
            let sample =
                (2.0 * std::f32::consts::PI * 125.0 * frame as f32 / 48_000.0).sin() * 0.25;
            samples.extend([sample, sample]);
        }
        assert!(engine.process(&mut samples));
        assert!(engine.auto_gains_db.iter().any(|gain| gain.abs() > 0.01));
        assert!(
            engine
                .auto_gains_db
                .iter()
                .all(|gain| (-3.0..=3.0).contains(gain))
        );
        engine.reset();
        assert!(
            engine
                .auto_gains_db
                .iter()
                .all(|gain| gain.abs() < f32::EPSILON)
        );
    }

    #[test]
    fn spectrum_updates_before_auto_eq_window() {
        let mut engine = AudioEngine::new(48_000, 2).unwrap();
        let mut samples = Vec::with_capacity(4_800);
        for frame in 0..2_400 {
            let sample =
                (2.0 * std::f32::consts::PI * 1_000.0 * frame as f32 / 48_000.0).sin() * 0.2;
            samples.extend([sample, sample]);
        }
        assert!(engine.process(&mut samples));
        assert!(engine.spectrum.iter().any(|level| *level > 0.0));
        assert_eq!(engine.auto_gains_db, [0.0; 10]);
    }

#[test]
fn master_bypass_disables_normalization() {
    let mut engine = AudioEngine::new(48_000, 2).unwrap();
    engine.configure(Config { enabled: false, normalization_enabled: true, ..Config::default() });
    let mut samples = vec![0.5; 48_000];
    assert!(engine.process(&mut samples));
    assert!(samples.iter().all(|s| (*s - 0.5).abs() < 1e-6));
}

#[test]
fn seek_reset_preserves_gain_envelopes() {
    let mut engine = AudioEngine::new(48_000, 2).unwrap();
    engine.configure(Config { enabled: true, normalization_enabled: true, output_gain_db: -6.0, ..Config::default() });
    engine.process(&mut vec![0.9; 96_000]);
    let compressor = engine.compressor_gain;
    let gain = engine.smoothed_gain;
    assert!(compressor < 1.0);
    engine.reset();
    assert_eq!(engine.compressor_gain, compressor);
    assert_eq!(engine.smoothed_gain, gain);
}
