# Implementation Plan: Comprehensive App Audit, Stabilization & Two-Pass Enhancement

This plan outlines the stabilization and systematic upgrade across all seven tabs of the BooDa Beat Enhancer Android app (**Analyze**, **Styles/Style Generator**, **Library**, **Build**, **Simple**, **Takes**, and **Advisor**).

---

## User Review Required

> [!IMPORTANT]
> - **Room Migration & Data Preservation**: The existing Room database schema (v1 → v2 → v3) preserves all existing `Take` records and `FavoritePrompt` items without destructive fallback (`fallbackToDestructiveMigration()` is completely removed).
> - **Dual Export Strategy**: As selected, Takes and Favorites will support both structured Suno text/markdown and cross-platform envelope JSON (`{ "app": "BooDa Beat Enhancer", "kind": "...", ... }`).
> - **Zero Placeholder Policy**: All features, checklists, analytics, and prompts will be implemented as production-grade Kotlin and Jetpack Compose code with no mock stubs.

---

## Proposed Changes

### Phase 1: Stabilization & Root-Cause Defect Resolution

Audit every tab for potential edge cases, state loss, or regressions, resolving each at its root cause:

1. **`AnalyzeScreen.kt` & `AudioAnalyzer.kt`**:
   - Add BPM half/double adjustment controls under measurement cards (`feels half: N BPM` / `feels double: N BPM`) to correct octave-doubling or halving on busy hi-hats.
   - Implement the Sample Vocal Honesty Law: mark transcribed sample words as `"Heard clearly"` or `"Fragment only"` up to 120 chars, defaulting to explicit `"No words made out"` if undetected rather than hallucinating lyrics.
   - Ensure clean resource release in `AudioRecorder` and `AudioAnalyzer` to avoid memory or mic lockups.

2. **`BuildScreen.kt`**:
   - Fix expansion lane lyrics fallback: ensure that any lane without a static bank template defaults smoothly to its `v6 detailed structure` (preventing blank lyrics maps).
   - Enforce input character length caps: 120 chars on take name, 1,000 on next change notes.
   - Ensure `assembleStyle` warns visibly if user text pushes prompt past the 1,000-character cap instead of silently truncating.

3. **`TakesScreen.kt` & `SimpleScreen.kt`**:
   - Fix take card edit inputs to stay synchronized with underlying reactive database flows.
   - Prevent out-of-range indices in Simple prompt assembler style pickers.

---

### Phase 2: Enhancement Pass 1 (Quick-Handoff & Build Assembly)

Deliver high-impact production features requested in the clarifying interview:

1. **Suno Handoff Checklist (`BuildScreen.kt`)**:
   - A dedicated 5-step checklist in Suno's exact interface order:
     1. Route selection (`Extend` with Audio Influence 75–85)
     2. Style prompt block (with lock line check)
     3. Exclude styles block (anti-hallucination / clean textures)
     4. Lyrics map (16-section structure ending with `[End]`)
     5. Tested slider settings (Variety 0, Weirdness 20–30, Style Influence 65–75)
   - One-tap copy for each section that automatically checks off the step.
   - Fingerprinted state tracking: resets checkboxes if the underlying prompt content is edited.

2. **Pre-Flight Readiness Check Card (`BuildScreen.kt`)**:
   - Real-time audit card that evaluates the package before copying:
     - Style character cap check (`<= 1,000` chars; highlights current count)
     - Mandatory sample preservation lock line present for Preserve jobs
     - Lyrics map cap check (`<= 5,000` chars) and `[End]` tag presence
     - Exclude negative prompt non-empty check
     - Variety parameter advisory (warns if Variety > 0 that Suno will rewrite style prompts)

3. **One-Tap Full Package from Analyze (`AnalyzeScreen.kt` & `MainViewModel.kt`)**:
   - Direct "Build Full Preserve Package" button under the measurement grid that populates the Build screen with style, analysis-fitted lyrics framework, simple prompt, and exclude defense in a single tap.

---

### Phase 2: Enhancement Pass 2 (Dual Export, Studio Analytics & Hardening)

1. **Dual Export System (`FavoriteRepository.kt`, `TakeRepository.kt`, `TakesScreen.kt`)**:
   - **Structured Suno Text**: Export clean, readable Markdown/plain-text files or clipboard strings with metadata, section headers, and prompt blocks formatted for Suno paste.
   - **Cross-Platform JSON Backup**: Export and non-destructive merge-only import conforming to the web artifact's envelope specification (`{ "app": "BooDa Beat Enhancer", "kind": "take-package-backup", "version": 1, ... }`).

2. **Takes Win-Rate & Studio Analytics (`TakesScreen.kt`)**:
   - Live win-rate analytics pill: `Winners ÷ (Winners + Misses)` percentage calculation.
   - Filterable tabs/chips: All, Winners, Pending, Misses, with live counts.
   - "Duplicate Take" action for rapid iteration on winning beats.

3. **Visual Polish & Touch Ergonomics (`design-guidelines` adherence)**:
   - Consistent 48dp minimum touch targets on all icon buttons, checkboxes, and chips.
   - Smooth Spring animations on checklist toggles, heart buttons, and copy confirmations.
   - High-contrast M3 dark studio palette (`#0A0C10` background, Amber Gold `#F5A524`, Studio Cyan `#00D2FF`).

---

## Verification Plan

### Automated Test Suite
Run after each phase using `gradle :app:testDebugUnitTest`:
1. `ExampleUnitTest.kt`:
   - Verify all 150 curated prompts are strictly `<= 200` characters.
   - Verify multi-word AND search query tokenizer with case-insensitivity and whitespace trimming.
   - Verify Pre-Flight readiness rules and character count bounds.
2. `ExampleRobolectricTest.kt`:
   - Room migration `MIGRATION_1_2` and `MIGRATION_2_3` without data loss.
   - `FavoritePromptDao` and `TakeDao` operations (insert, duplicate prevention via `OnConflictStrategy.REPLACE`, delete, flow ordering).
   - Dual export/import round-trip: envelope JSON and structured text formats.
   - Take win-rate calculation logic.

### Manual Verification
1. Launch app on emulator and verify all 7 bottom tabs (Analyze, Styles, Library, Build, Simple, Takes, Advisor).
2. Record / upload audio in **Analyze** → click "Build Full Preserve Package" → verify instant population in **Build**.
3. In **Build**, test the Pre-Flight checklist and Suno Handoff checklist, verifying one-tap copy and tick-off states.
4. In **Styles**, search `808 hats`, verify term highlighting and live result counts.
5. In **Takes**, test "Duplicate Take", test "Copy Full Package", and test "Download / Export" (both JSON and Text).
6. Verify clean build via `compile_applet`.
