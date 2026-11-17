package com.example.data.repository

import com.example.data.db.PromptTemplateDao
import com.example.data.model.EXCLUDE_LIMIT
import com.example.data.model.JobType
import com.example.data.model.LOCK_LINE
import com.example.data.model.LYRICS_LIMIT
import com.example.data.model.PromptTemplateEntity
import com.example.data.model.STYLE_LIMIT
import kotlinx.coroutines.flow.Flow

class PromptTemplateRepository(private val dao: PromptTemplateDao) {

  val allTemplates: Flow<List<PromptTemplateEntity>> = dao.observeAllTemplates()
  val categories: Flow<List<String>> = dao.observeCategories()

  suspend fun ensureSeeded() {
    if (dao.getCount() == 0) {
      val now = System.currentTimeMillis()
      dao.insertAll(starterTemplates(now))
    }
  }

  suspend fun restoreStarterTemplates() {
    val now = System.currentTimeMillis()
    starterTemplates(now).forEach { starter ->
      val existing = dao.findByTitleAndCategory(starter.title, starter.category)
      if (existing == null) {
        dao.insertTemplate(starter)
      }
    }
  }

  suspend fun saveTemplate(
    title: String,
    category: String,
    lane: String,
    jobType: JobType,
    bpmHint: String,
    stylePrompt: String,
    lyricsMap: String,
    excludeText: String,
    variety: Int,
    weirdness: Int,
    styleInfluence: Int,
    audioInfluence: Int,
    isPinned: Boolean = true
  ): Long {
    val cleanTitle = title.trim().ifEmpty { "$lane Preset" }.take(60)
    val cleanCategory = category.trim().ifEmpty { "Custom" }.take(40)
    val cleanStyle = stylePrompt.trim().take(STYLE_LIMIT)
    val cleanLyrics = lyricsMap.take(LYRICS_LIMIT)
    val cleanExclude = excludeText.trim().take(EXCLUDE_LIMIT)
    val now = System.currentTimeMillis()

    return dao.insertTemplate(
      PromptTemplateEntity(
        title = cleanTitle,
        category = cleanCategory,
        lane = lane.ifBlank { "Trap" },
        jobType = jobType.value,
        bpmHint = bpmHint.trim().take(24),
        stylePrompt = cleanStyle,
        lyricsMap = cleanLyrics,
        excludeText = cleanExclude,
        variety = variety.coerceIn(0, 100),
        weirdness = weirdness.coerceIn(0, 100),
        styleInfluence = styleInfluence.coerceIn(0, 100),
        audioInfluence = audioInfluence.coerceIn(0, 100),
        useCount = 1,
        isPinned = isPinned,
        isStarter = false,
        createdAt = now,
        lastUsedAt = now
      )
    )
  }

  suspend fun recordUsage(id: Long, usedAt: Long = System.currentTimeMillis()) {
    dao.incrementUsage(id, usedAt)
  }

  suspend fun setPinned(id: Long, pinned: Boolean) {
    dao.setPinned(id, pinned)
  }

  suspend fun deleteById(id: Long) {
    dao.deleteById(id)
  }

  companion object {
    val DEFAULT_CATEGORIES = listOf(
      "Lo-Fi Hip Hop",
      "Dark Synthwave",
      "Trap",
      "UK Drill",
      "Boom Bap",
      "Afro-Trap",
      "Custom"
    )

    fun filterTemplates(
      templates: List<PromptTemplateEntity>,
      category: String,
      query: String
    ): List<PromptTemplateEntity> {
      val tokens = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
      return templates.filter { item ->
        val matchesCategory = category == "All" || item.category.equals(category, ignoreCase = true)
        val matchesQuery = if (tokens.isEmpty()) {
          true
        } else {
          val haystack = "${item.title} ${item.category} ${item.lane} ${item.stylePrompt} ${item.bpmHint} ${item.excludeText}".lowercase()
          tokens.all { token -> haystack.contains(token) }
        }
        matchesCategory && matchesQuery
      }
    }

    fun starterTemplates(baseTimeMs: Long = System.currentTimeMillis()): List<PromptTemplateEntity> = listOf(
      PromptTemplateEntity(
        title = "Dusty Rhodes Lo-Fi",
        category = "Lo-Fi Hip Hop",
        lane = "Jazz rap / lo-fi",
        jobType = JobType.ORIGINAL.value,
        bpmHint = "82 BPM",
        stylePrompt = "Warm dusty Rhodes chords, SP-404 vinyl crackle, brushed snare pocket, deep round upright bass, late night lo-fi hip hop instrumental, 82 BPM.",
        lyricsMap = "[Intro: Vinyl Crackle & Warm Rhodes]\n[Verse 1: Laidback Boom Swing]\n[Bridge: Tape Wow & Sparse Keys]\n[Outro: Fade Into Rain]\n[End]",
        excludeText = "harsh digital clipping, loud edm risers, fast trap hats, vocals",
        variety = 18,
        weirdness = 24,
        styleInfluence = 74,
        audioInfluence = 0,
        useCount = 3,
        isPinned = true,
        isStarter = true,
        createdAt = baseTimeMs - 80_000L,
        lastUsedAt = baseTimeMs - 10_000L
      ),
      PromptTemplateEntity(
        title = "Midnight Neon Synthwave",
        category = "Dark Synthwave",
        lane = "Memphis phonk / dark trap",
        jobType = JobType.ORIGINAL.value,
        bpmHint = "108 BPM",
        stylePrompt = "Brooding analog Juno bassline, gated 80s snare reverb, dark synthwave arpeggios, VHS tape saturation, cinematic noir chase drive, 108 BPM.",
        lyricsMap = "[Intro: Analog Pulse & Rain Synth]\n[Build: Arpeggio Filter Open]\n[Drop: Heavy Gated Snare & Saw Bass]\n[Breakdown: Atmospheric Pad Hold]\n[Drop: Full Neon Drive]\n[Outro: Tape Stop Decay]\n[End]",
        excludeText = "acoustic country guitar, cheerful pop, brass fanfare, vocals",
        variety = 28,
        weirdness = 42,
        styleInfluence = 84,
        audioInfluence = 0,
        useCount = 3,
        isPinned = true,
        isStarter = true,
        createdAt = baseTimeMs - 70_000L,
        lastUsedAt = baseTimeMs - 20_000L
      ),
      PromptTemplateEntity(
        title = "808 Trunk Anthem",
        category = "Trap",
        lane = "Trap",
        jobType = JobType.ORIGINAL.value,
        bpmHint = "140 BPM",
        stylePrompt = "Hard Atlanta trap instrumental, trunk-rattling tuned 808 sub, crisp rolling hi-hats, sparse ominous bell motif, sharp clap on 3, 140 BPM.",
        lyricsMap = "[Intro: Dark Bell Motif]\n[Pre-Drop: Snare Roll Build]\n[Drop: Heavy 808 & Rolling Hats]\n[Verse: Half-Time Pocket Space]\n[Outro: Sub Tail]\n[End]",
        excludeText = "jazz swing, muddy midrange, vocals, cheesy brass",
        variety = 22,
        weirdness = 30,
        styleInfluence = 78,
        audioInfluence = 0,
        useCount = 2,
        isPinned = true,
        isStarter = true,
        createdAt = baseTimeMs - 60_000L,
        lastUsedAt = baseTimeMs - 30_000L
      ),
      PromptTemplateEntity(
        title = "Croydon Sliding 808",
        category = "UK Drill",
        lane = "UK drill",
        jobType = JobType.ORIGINAL.value,
        bpmHint = "142 BPM",
        stylePrompt = "Dark UK drill instrumental, distorted sliding 808 glides, syncopated offbeat snare on 3, eerie choir pads, tight triplet hi-hat stutters, 142 BPM.",
        lyricsMap = "[Intro: Ghost Choir & Reverse Cymbal]\n[Verse 1: Sliding 808 Entry]\n[Chorus: Full Syncopated Drill Kit]\n[Outro: Isolated Piano]\n[End]",
        excludeText = "four on the floor, bright tropical plucks, vocals",
        variety = 25,
        weirdness = 36,
        styleInfluence = 82,
        audioInfluence = 0,
        useCount = 1,
        isPinned = false,
        isStarter = true,
        createdAt = baseTimeMs - 50_000L,
        lastUsedAt = baseTimeMs - 40_000L
      ),
      PromptTemplateEntity(
        title = "Rainy Tape Preserve",
        category = "Lo-Fi Hip Hop",
        lane = "Jazz rap / lo-fi",
        jobType = JobType.PRESERVE.value,
        bpmHint = "86 BPM",
        stylePrompt = "$LOCK_LINE. Add dusty lo-fi hip hop brush kit underneath only, warm sub thump, sample stays lead, 86 BPM.",
        lyricsMap = "[Intro: Original Sample Lead]\n[Verse: Tight Brush Drums Underneath]\n[Outro: Sample Solo Fade]\n[End]",
        excludeText = "re-harmonize melody, loud supersaws, busy hi-hat rolls",
        variety = 12,
        weirdness = 18,
        styleInfluence = 68,
        audioInfluence = 86,
        useCount = 1,
        isPinned = false,
        isStarter = true,
        createdAt = baseTimeMs - 40_000L,
        lastUsedAt = baseTimeMs - 50_000L
      ),
      PromptTemplateEntity(
        title = "Cyber Noir Preserve",
        category = "Dark Synthwave",
        lane = "Dark trap",
        jobType = JobType.PRESERVE.value,
        bpmHint = "116 BPM",
        stylePrompt = "$LOCK_LINE. Add dark synthwave gated drums and deep analog sub underneath only, crisp pocket, sample stays lead, 116 BPM.",
        lyricsMap = "[Intro: Original Sample Space]\n[Drop: Gated Snare & Analog Sub Entry]\n[Outro: Reverb Decay]\n[End]",
        excludeText = "acoustic swing, upbeat disco, overpowering lead synths",
        variety = 20,
        weirdness = 32,
        styleInfluence = 75,
        audioInfluence = 84,
        useCount = 1,
        isPinned = false,
        isStarter = true,
        createdAt = baseTimeMs - 30_000L,
        lastUsedAt = baseTimeMs - 60_000L
      ),
      PromptTemplateEntity(
        title = "SP-1200 Soul Chop",
        category = "Boom Bap",
        lane = "Boom bap",
        jobType = JobType.PRESERVE.value,
        bpmHint = "92 BPM",
        stylePrompt = "$LOCK_LINE. Add crunchy SP-1200 boom bap kick, snapping snare on 2 and 4, 58% MPC swing underneath only, 92 BPM.",
        lyricsMap = "[Intro: Vinyl Dust & Chop Preview]\n[Verse 1: Crunchy Boom Bap Pocket]\n[Break: Drums Drop Out]\n[Outro: Chop Fade]\n[End]",
        excludeText = "808 glides, trap triplets, autotune, gloss",
        variety = 15,
        weirdness = 22,
        styleInfluence = 76,
        audioInfluence = 88,
        useCount = 1,
        isPinned = false,
        isStarter = true,
        createdAt = baseTimeMs - 20_000L,
        lastUsedAt = baseTimeMs - 70_000L
      ),
      PromptTemplateEntity(
        title = "Lagos Log-Drum Bounce",
        category = "Afro-Trap",
        lane = "Afro-trap",
        jobType = JobType.ORIGINAL.value,
        bpmHint = "112 BPM",
        stylePrompt = "Afro-trap instrumental, wooden log-drum bass anchor, shaker & djembe polyrhythm, crisp trap clap, warm plucked guitar chords, 112 BPM.",
        lyricsMap = "[Intro: Shakers & Plucked Motif]\n[Verse 1: Log-Drum Polyrhythm Groove]\n[Chorus: Full Percussion & Sub Drop]\n[Outro: Percussion Ride]\n[End]",
        excludeText = "heavy metal distortion, murky sub mud, vocals",
        variety = 26,
        weirdness = 28,
        styleInfluence = 80,
        audioInfluence = 0,
        useCount = 1,
        isPinned = false,
        isStarter = true,
        createdAt = baseTimeMs - 10_000L,
        lastUsedAt = baseTimeMs - 80_000L
      )
    )
  }
}
