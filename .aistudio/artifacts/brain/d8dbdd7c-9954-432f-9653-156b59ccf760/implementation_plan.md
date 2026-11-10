# BooDa Beat Enhancer — Style Generator Expansion & Quality Hardening Plan

Comprehensive plan to implement the searchable filter bar, Room-persisted favorites system with zero data loss migration, keyword highlighting, prompt sharing, and an exhaustive diagnostic audit with automated verification.

## User Review & Critical Decisions

> [!IMPORTANT]
> - **Room Migration (v2 → v3)**: Existing `takes` table data is preserved with an explicit `Migration(2, 3)` creating the `favorite_prompts` table, removing `fallbackToDestructiveMigration()`.
> - **Search Filter AND Logic**: Multi-word search terms (e.g., "808 hats") require all tokens to match across prompt text, category, or subgenre/tags in any order.
> - **Suno v6 Length Limit Guard**: All 150+ curated prompts are enforced ≤ 200 characters with an automated unit test.
> - **Single Source of Truth**: Room DAO exposes `Flow<List<FavoritePromptEntity>>` and `Flow<Set<Long>>`, ensuring instant reactive heart sync across all chips, search queries, and views.

---

## 1. Overview & Core Concept

- **What It Delivers**: 
  1. A persistent, debounced, case-insensitive multi-word search bar in the **Style Generator** tab with term highlighting and live result counters.
  2. A persistent **Room Favorites System** with animated heart toggles, haptics, snackbar with undo, and favorites export/import.
  3. A robust **v2 → v3 Room migration** ensuring zero data loss for existing Take records.
  4. An **Exhaustive Diagnostic Audit** fixing lifecycle leaks, audio buffer edge cases, and Gemini timeout handling.
  5. Enhanced prompt actions including system share sheet and recent prompts history.

---

## 2. User Experience & Visual Design

### Key User Flows
1. **Searching & Filtering**:
   - User opens the **Style Generator** tab.
   - Types queries like `"808 hats"`, `"gospel soul"`, or `"drill"`.
   - The list updates in real-time with a ~250ms debounce. Matched words are highlighted in Amber Gold against the dark prompt background.
   - The result counter dynamically displays `"14 of 150 prompts"`.
   - Tapping the clear button `(X)` clears the search text and keeps the active category chip intact.
   - If no prompts match, a styled empty state appears with a `"Clear filters"` button.

2. **Favoriting & Managing Prompts**:
   - Every prompt card features an animated Heart button (`Icons.Default.Favorite` vs `Icons.Default.FavoriteBorder`).
   - Tapping toggles the heart with a bounce animation and tactile haptic feedback.
   - A Snackbar appears confirming `"Added to Favorites"` or `"Removed from Favorites"` with an `"Undo"` action.
   - Switching to the **Favorites** chip shows only saved prompts with an item count badge (e.g., `Favorites (8)`).
   - If no favorites exist yet, an empty state guides the user: *"Tap the heart on any prompt to save it here."*

3. **Prompt Actions**:
   - **Copy**: One-tap copy with animated checkmark and toast confirmation.
   - **Share**: System Android Sharesheet to send style prompts to Notes, messaging, or Suno web.
   - **Send to Build**: Immediately stages the 200-char style prompt into the 3-Box Suno studio.

### Design System & Tokens
- **Theme**: Obsidian Dark Console (`StudioBg = #0A0C10`, `StudioCard = #12151D`, `StudioCardElevated = #1A1F2B`).
- **Accents**: Amber Gold (`#F5A524`) for primary beats/favorites, Studio Cyan (`#00D2FF`) for DSP/metrics, Studio Miss (`#EF4444`) for clipping/removal.
- **Accessibility**: All buttons and icon targets strictly sized to minimum 48dp x 48dp with semantic `contentDescription`.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Explicit Room Migration vs Destructive Fallback**:
  - *Chosen Approach*: Implement `MIGRATION_2_3` creating `favorite_prompts` table with `id` (Long primary key), `prompt` (TEXT), `category` (TEXT), `subgenre` (TEXT), `bpmHint` (TEXT), and `savedAt` (INTEGER NOT NULL). Remove `fallbackToDestructiveMigration()`.
  - *Why*: User data in the Take Logbook must never be wiped across updates.
- **Decision 2: In-Memory Search Debounce + Highlighted Text Rendering**:
  - *Chosen Approach*: StateFlow debounce (250ms) driving `remember(query, category, favorites)` with `buildAnnotatedString` applying `SpanStyle(background = AmberGold.copy(alpha = 0.25f), color = AmberGold)` to matching substrings.
  - *Why*: Delivers 60 FPS scrolling while giving instant visual feedback on why a prompt matched.
- **Decision 3: Conflict Strategy REPLACE for Favorites**:
  - *Chosen Approach*: `OnConflictStrategy.REPLACE` in `FavoritePromptDao`.
  - *Why*: Eliminates primary key collisions or duplicate database rows on rapid double taps.

---

## 4. Technical Architecture & Data Strategy

```
┌────────────────────────────────────────────────────────┐
│               StyleGeneratorScreen                     │
│  ┌──────────────────────────────────────────────────┐  │
│  │ Persistent Search Bar (Debounced 250ms, Clear X)  │  │
│  └──────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────┐  │
│  │ Category Chips: All | Beef-Up | Gospel | Favorites│  │
│  └──────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────┐  │
│  │ Live Result Counter: "12 of 150 prompts"          │  │
│  └──────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────┐  │
│  │ LazyColumn (CuratedPromptItemCard)               │  │
│  │  - Matched Term Highlighting (AnnotatedString)   │  │
│  │  - Animated Heart Toggle (Scale + Color)         │  │
│  │  - One-Tap Copy, Send-to-Build, System Share     │  │
│  └──────────────────────────────────────────────────┘  │
└──────────────────────────┬─────────────────────────────┘
                           │
                           ▼
┌────────────────────────────────────────────────────────┐
│                   MainViewModel                        │
│  - searchQuery: StateFlow<String>                      │
│  - selectedCategory: StateFlow<String>                 │
│  - favoriteIds: StateFlow<Set<Long>>                   │
│  - filteredPrompts: StateFlow<List<CuratedSunoPrompt>> │
│  - toggleFavorite(prompt) / undoRemoveFavorite()       │
└──────────────────────────┬─────────────────────────────┘
                           │
                           ▼
┌────────────────────────────────────────────────────────┐
│         Room Database (v3 with MIGRATION_2_3)          │
│  ┌───────────────────────┐   ┌──────────────────────┐  │
│  │      TakeDao          │   │  FavoritePromptDao   │  │
│  │  (existing takes)     │   │  (saved favorites)   │  │
│  └───────────────────────┘   └──────────────────────┘  │
└────────────────────────────────────────────────────────┘
```

### Entities & DAOs
1. **`FavoritePromptEntity`**:
   - `id: Long` (Primary Key matching `CuratedSunoPrompt.id`)
   - `prompt: String`
   - `category: String`
   - `subgenre: String`
   - `bpmHint: String`
   - `savedAt: Long` (Epoch millis for sorting)
2. **`FavoritePromptDao`**:
   - `getAllFavorites(): Flow<List<FavoritePromptEntity>>` (ORDER BY savedAt DESC)
   - `getFavoriteIds(): Flow<List<Long>>`
   - `insertFavorite(entity: FavoritePromptEntity): Long`
   - `deleteFavoriteById(id: Long)`
   - `isFavorite(id: Long): Boolean`

---

## 5. Verification & Test Plan

1. **Automated Unit Tests**:
   - **Suno 200-Character Limit Verification**: Verify all 150+ prompts in `CuratedPrompts.ALL_CURATED` are strictly ≤ 200 characters.
   - **Search Logic Tests**: Test case-insensitivity, whitespace trimming, multi-word AND matching (`"808 hats"`), and zero-match empty state.
   - **Room Favorites & Migration Tests**: Test insertion, duplicate prevention, deletion, and order preservation.
2. **Robolectric & Compilation**:
   - Verify migration from v2 to v3 schema without data loss.
   - Run `gradle :app:testDebugUnitTest` and `compile_applet`.
