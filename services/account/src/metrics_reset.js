const number = value => Number.isSafeInteger(value) && value >= 0 ? value : 0;
const map = value => value && typeof value === "object" && !Array.isArray(value) ? value : {};

export function countersAfterReset(clock, floor = {}) {
  const subtract = (values, baseline) => Object.fromEntries(Object.entries(map(values))
    .map(([id, value]) => [id, Math.max(0, number(value) - number(baseline?.[id]))]));
  const days = Object.fromEntries(Object.entries(map(clock.days)).map(([key, day]) => [key, {
    ...day, listens: Math.max(0, number(day.listens) - number(floor.days?.[key]?.listens)),
    heardMs: Math.max(0, number(day.heardMs) - number(floor.days?.[key]?.heardMs)),
  }]));
  return { counts: subtract(clock.counts, floor.counts), heard: subtract(clock.heard, floor.heard), days };
}

/** Keep cumulative device clocks so a retry cannot restore erased listens. */
export function resetLibraryMetrics(previous, at = Date.now()) {
  const state = previous || { version: 2, library: {}, clocks: {} };
  return { ...state, version: 2, metricsResetAt: at,
    counterFloors: structuredClone(state.clocks || {}), baselineCounts: {}, baselineDays: {}, baselineHeard: 0,
    library: { ...state.library, playCounts: {}, listeningDays: [], listeningTotalMs: 0 } };
}
