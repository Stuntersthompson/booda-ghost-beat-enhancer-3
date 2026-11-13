package com.example.data.repository

import android.content.Context
import com.example.data.model.*
import org.json.JSONObject
import java.io.InputStreamReader

class PromptBankRepository(private val context: Context) {

  private var initialized = false

  val stylePrompts = mutableListOf<StylePrompt>()
  val gospelPrompts = mutableListOf<GospelPrompt>()
  val slotTemplates = mutableListOf<SlotTemplate>()
  val lyricsBoxTemplates = mutableListOf<LyricsTemplate>()
  val excludeBanks = mutableListOf<ExcludeBank>()
  val settingsCards = mutableListOf<SettingsCard>()

  val verifiedLanes = mutableListOf<String>()
  val universalLanes = mutableListOf<String>()
  val lyricsTopics = mutableListOf<String>()

  // 20 Expansion Lanes
  val expansionLanes = listOf(
    ExpansionLane("Milwaukee low-end", "Milwaukee / Midwest", "low-end bounce pocket, clap-heavy backbeat, short punchy 808 stabs, sparse hats, piano-led bounce", "92-112 BPM", "clap-heavy bounce, short 808 stabs, sparse hats", "punchy 808 stabs"),
    ExpansionLane("DMV crank", "DMV", "crank pocket with go-go style conga bounce, heavy kick, sharp snare, short 808 hits, call-response percussion", "95-118 BPM", "conga bounce, heavy kick, sharp snare, short 808", "short 808 hits"),
    ExpansionLane("Jerk", "West Coast new wave", "jerk bounce, pin-drop kick pattern, snap and clap accents, short 808, playful dance-pocket swing", "88-108 BPM", "pin-drop kicks, snap accents, short 808, swing", "short 808"),
    ExpansionLane("NY sample drill", "New York", "sample-led drill pocket, sliding 808 glides under the chop, punchy syncopated snare, tight hats", "135-150 BPM", "sliding 808 glides, syncopated snare, tight hats", "sliding 808 glides"),
    ExpansionLane("Afro-trap", "West Africa / global", "shaker and djembe polyrhythm over trap hats, melodic 808 support, log-drum touches, rolling pocket", "100-122 BPM", "shaker polyrhythm, trap hats, melodic 808", "melodic 808 support"),
    ExpansionLane("Hood trap", "Southern / street trap", "raw trap pocket, half-time snare on 3, heavy long 808, crisp clap, sparse arrangement, space and tension", "130-150 BPM", "half-time snare on 3, heavy long 808, crisp clap", "heavy long 808"),
    ExpansionLane("Detroit scam-rap pocket", "Detroit", "frantic off-beat hats and crisp claps pushed ahead of the beat, sparse bending 808, urgent rushed pocket", "150-175 BPM", "off-beat hats, crisp claps ahead, bending 808", "sparse bending 808"),
    ExpansionLane("Florida fast", "Florida", "fast pocket, rapid hat runs, hard kick bursts, short 808 stabs, bright synth stabs, high-energy bounce", "145-170 BPM", "rapid hats, kick bursts, short 808 stabs", "short 808 stabs"),
    ExpansionLane("Texas slab", "Texas", "slab pocket, slow heavy sub weight, laid-back snare behind the beat, sparse hats, trunk low end", "68-88 BPM", "slow heavy sub, laid-back snare, sparse hats", "slow heavy sub weight"),
    ExpansionLane("Chopped & screwed", "Houston / Texas", "slowed dragged pocket, stutter-chop edits, heavy echo tails, deep slowed 808, syrupy swing", "60-78 BPM", "slowed pocket, stutter chops, deep slowed 808", "deep slowed 808"),
    ExpansionLane("Drift phonk", "Phonk / drift", "cowbell melody as lead percussion, distorted saturated 808, triplet hats, tape hiss, sliding pocket", "120-145 BPM", "cowbell lead, saturated 808, triplet hats", "distorted saturated 808"),
    ExpansionLane("Dark trap", "Trap core", "sparse dark trap pocket, heavy distorted 808, slow hat rolls, tension space between hits", "125-145 BPM", "sparse pocket, distorted 808, slow hat rolls", "heavy distorted 808"),
    ExpansionLane("Boom trap", "Hybrid", "swung break chops with modern rolling 808 and un-swung trap hat subdivisions, snare on 2 and 4", "85-105 BPM", "swung break chops, rolling 808, trap hats", "modern rolling 808"),
    ExpansionLane("Afro-drill", "UK / Afrobeats cross", "sliding 808 glides with log-drum percussion, Afrobeats syncopation, tight drill hats, dark pocket", "135-148 BPM", "808 glides, log-drum percussion, drill hats", "sliding 808 glides"),
    ExpansionLane("Latin trap", "Latin / Caribbean cross", "dembow tresillo 3-3-2 syncopation under trap hats, heavy 808, rolling pocket with a dance pull", "86-100 BPM", "dembow tresillo, trap hats, heavy 808", "heavy 808"),
    ExpansionLane("Amapiano hip-hop", "South Africa / global", "log-drum bass as sub and rhythm anchor, shaker pocket, hip-hop kick and snare layer, rolling groove", "108-118 BPM", "log-drum anchor, shaker pocket, hip-hop kit", "log-drum bass anchor"),
    ExpansionLane("Grime", "UK", "syncopated garage-style drums at a fast clip, sparse square sub stabs, empty midrange, sharp hats", "136-144 BPM", "syncopated drums, square sub stabs, sharp hats", "sparse square sub stabs"),
    ExpansionLane("New Orleans bounce", "New Orleans", "fast signature breakbeat, relentless shaker and snare, call-response percussion, repetitive bounce pocket", "95-112 BPM", "fast breakbeat, shaker and snare, bounce pocket", "heavy bounce sub"),
    ExpansionLane("Digicore / glitch trap", "Internet / experimental", "glitched trap drums, micro-edited kicks, pitch-shifted chops, bitcrushed texture, maximalist edits", "140-165 BPM", "glitched drums, micro-edits, bitcrushed chops", "bitcrushed 808"),
    ExpansionLane("Tread", "Experimental / fast", "ultra-fast relentless pocket, chopped break runs, lo-fi kick and snare velocity, minimal aggressive design", "175-195 BPM", "ultra-fast breaks, lo-fi kick runs, hard cuts", "deep sub pulse")
  )

  val allLanes = mutableListOf<String>()

  val curatedPrompts: List<CuratedSunoPrompt> = CuratedPrompts.ALL_CURATED

  fun getCurated(category: String = "All", query: String = ""): List<CuratedSunoPrompt> {
    val trimmed = query.trim()
    val tokens = if (trimmed.isEmpty()) emptyList() else trimmed.split(Regex("\\s+")).filter { it.isNotEmpty() }

    return curatedPrompts.filter { item ->
      val matchesCat = if (category == "All") true else item.category.equals(category, ignoreCase = true)
      val matchesQuery = if (tokens.isEmpty()) {
        true
      } else {
        val target = "${item.prompt} ${item.category} ${item.subgenre} ${item.bpmHint}".lowercase()
        tokens.all { token -> target.contains(token.lowercase()) }
      }
      matchesCat && matchesQuery
    }
  }

  fun composeSuno200CharPrompt(
    sampleType: String,
    drumStyle: String,
    vibe: String,
    bpm: Int,
    isPreserve: Boolean
  ): String {
    val prefix = if (isPreserve) "Preserve original $sampleType." else "$sampleType instrumental."
    val drums = "Add $drumStyle and tuned 808 sub."
    val suffix = "$vibe. $bpm BPM."
    val full = "$prefix $drums $suffix"
    return if (full.length <= 200) full else full.take(200)
  }

  val frameworks = mapOf(
    "Trap" to FrameworkProfile("Trap Anchor Frame", "Intro 8 · 3× Verse 16 · 3× Chorus 8 · Outro 8", "riser climbs, snare roll builds from 8ths to 32nds", "maximum drum and bass weight", "drums out, melody and tops only", "snare rolls, half-time switch, stop-time cuts"),
    "Chiraq drill" to FrameworkProfile("Chiraq Dread Frame", "Intro 8 · 3× Verse 16 · 3× Chorus 8 · Outro 8", "dark riser, rapid hi-hat acceleration", "hard-hitting 808 with sliding glides", "bleak bells and strings only", "808 glides, aggressive snare rolls"),
    "UK drill" to FrameworkProfile("UK Sliding Frame", "Intro 8 · 3× Verse 16 · 3× Chorus 8 · Outro 8", "reverse cymbal and accelerating triplets", "distorted sliding 808 and syncopated snare", "sparse piano and vinyl hiss", "extreme 808 pitch glides, offbeat snare on 3"),
    "NY drill" to FrameworkProfile("NY Sample-Drill Frame", "Intro 8 · 3× Verse 16 · 3× Chorus 8 · Outro 8", "pitch-bent riser with frantic hi-hats", "punchy sliding 808 hitting hard", "exposed vocal sample chop", "triplet hi-hat rolls, vocal chop stutters"),
    "Boom bap" to FrameworkProfile("Classic SP-1200 Frame", "Intro 8 · 3× Verse 16 · 3× Chorus 8 · Outro 8", "subtle vinyl crackle build and drum roll", "crunchy kick and snapping snare crack", "isolated soul chop with upright bass", "54-62% MPC swing, open hi-hat on upbeat"),
    "Boom bap revival / conscious" to FrameworkProfile("Organic Boom Frame", "Intro 8 · 3× Verse 16 · 3× Chorus 8 · Outro 8", "warm brass swell and rolling snare fill", "warm analog kick with tight rimshot", "bare electric piano and vinyl dust", "humanized swing, subtle upright bass groove"),
    "Detroit trap" to FrameworkProfile("Detroit Scam Frame", "Intro 4 · 3× Verse 16 · 3× Chorus 8 · Outro 4", "frantic offbeat claps stacking ahead", "urgent rolling 808 with laser snares", "eerie minor piano loop running solo", "rushed claps, bent 808 accents"),
    "West Coast / G-funk" to FrameworkProfile("G-Funk Laidback Frame", "Intro 8 · 3× Verse 16 · 3× Chorus 8 · Outro 8", "whistling saw synth swell", "heavy thumping kick and deep sine wave sub", "whining synth lead and handclaps only", "portamento synth leads, finger snaps"),
    "Memphis phonk / dark trap" to FrameworkProfile("Memphis Tape Frame", "Intro 8 · 3× Verse 16 · 3× Chorus 8 · Outro 8", "lo-fi cassette hiss and cowbell stutter", "saturated distorted 808 slam", "chopped horror soundtrack sample", "distorted 808, cassette tape stops"),
    "Gospel trap" to FrameworkProfile("Sanctuary Frame", "Intro 8 · 3× Verse 16 · 3× Chorus 8 · Outro 8", "B3 Hammond organ swell and building claps", "triumphant 808 drop with worship piano", "pure B3 organ and solo vocal worship", "jubilant organ runs, rolling trap hats"),
    "Cloud rap / dreamo" to FrameworkProfile("Ethereal Space Frame", "Intro 8 · 3× Verse 16 · 3× Chorus 8 · Outro 8", "cavernous reverb swell and washed vocal pads", "submerged sub bass and whisper-soft hats", "floating synth ambient pad solo", "heavy sidechain pumping, pitched vocal echoes"),
    "Trap soul" to FrameworkProfile("Late Night Frame", "Intro 8 · 3× Verse 16 · 3× Chorus 8 · Outro 8", "filtered Rhodes chord swell", "warm round 808 with sensual finger snaps", "filtered R&B vocal chop in isolation", "low-pass filter sweeps, smooth 808"),
    "Jersey club rap" to FrameworkProfile("Newark Bounce Frame", "Intro 4 · 3× Verse 16 · 3× Chorus 8 · Outro 4", "signature fast triplet bed squeak build", "relentless 5-beat kick pattern drop", "chopped sample loop with kick dropouts", "5-beat triplet kick, bed squeak accents"),
    "Plugg" to FrameworkProfile("Plugg Melody Frame", "Intro 4 · 3× Verse 16 · 3× Chorus 8 · Outro 4", "bubbly bell melody climbing", "Zaytoven-style short 808 and hard crash", "chime bells and dry flute solo", "sparse hats, dry crash on the 1"),
    "Pluggnb" to FrameworkProfile("Plugg Romantic Frame", "Intro 4 · 3× Verse 16 · 3× Chorus 8 · Outro 4", "electric piano chord progression swell", "melodic 808 bounce with crisp snaps", "warm EP keys and romantic vocal chop", "smooth R&B chords, energetic hats"),
    "Rage" to FrameworkProfile("Hyper Saw Frame", "Intro 4 · 3× Verse 16 · 3× Chorus 8 · Outro 4", "bright detuned supersaw riser", "distorted wall of 808 fuzz", "soaring lead synth solo", "EDM-inspired supersaw leads, clipped 808"),
    "Horrorcore" to FrameworkProfile("Dread Space Frame", "Intro 8 · 3× Verse 16 · 3× Chorus 8 · Outro 8", "detuned church bell and heart pulse", "heavy low-frequency drone tail", "dissonant music box melody", "detuned hits, ominous cavernous cuts"),
    "Soul-sample hip-hop" to FrameworkProfile("Chop & Warmth Frame", "Intro 8 · 3× Verse 16 · 3× Chorus 8 · Outro 8", "brass horn swell under chopped soul loop", "full soul chop with warm round bassline", "bare soul vocals and warm vinyl tone", "vocal chops, swung rimshots"),
    "Down South / Southern trap" to FrameworkProfile("Trunk Frame", "Intro 8 · 3× Verse 16 · 3× Chorus 8 · Outro 8", "rolling hi-hat triplet crescendo", "trunk-rattling long 808 landing", "melody and ambient tops only", "long 808 decay, loud rimshots"),
    "East Coast" to FrameworkProfile("Hard Snare Frame", "Intro 8 · 3× Verse 16 · 3× Chorus 8 · Outro 8", "sharp snare fill and vinyl stop", "hardest snapping snare on 2 and 4", "dusty jazz guitar chop", "hard acoustic kicks, dry snappy snares"),
    "Jazz rap / lo-fi" to FrameworkProfile("Dust & Swing Frame", "Intro 8 · 3× Verse 16 · 3× Chorus 8 · Outro 8", "tape noise swell with soft brush fill", "warm upright double bass return", "mellow Rhodes chords and vinyl crackle", "tape saturation, laid-back swing"),
    // Expansion Profiles
    "Milwaukee low-end" to FrameworkProfile("Low-End Bounce Frame", "Intro 4 · 3× Verse 16 · 3× Chorus 8 · Outro 4", "clap build — handclaps stack bar by bar", "short punchy 808 stabs with piano lead", "piano riff and handclaps only", "clap stacks, 808 stab answers"),
    "DMV crank" to FrameworkProfile("Crank Call-Response Frame", "Intro 4 · 3× Verse 16 · 3× Chorus 8 · Outro 4", "conga build — call-response percussion stacks", "percussion answer with heavy kick", "congas and tops only, kick pulled", "call-response fills, conga runs"),
    "Jerk" to FrameworkProfile("Pin-Drop Bounce Frame", "Intro 4 · 3× Verse 16 · 3× Chorus 8 · Outro 4", "snaps and claps trade bars", "pin-drop kick pattern drop", "synth stabs and snaps only", "pin-drop kicks, snap accents"),
    "NY sample drill" to FrameworkProfile("Sample-First Drill Frame", "Intro 8 · 3× Verse 16 · 3× Chorus 8 · Outro 8", "riser under the chop, hats tighten", "sliding 808 entry under the vocal chop", "vocal chop alone, glide tail exposed", "chop flips, 808 glides, syncopated snare"),
    "Afro-trap" to FrameworkProfile("Polyrhythm Roll Frame", "Intro 8 · 3× Verse 16 · 3× Chorus 8 · Outro 8", "shaker build — percussion layers enter", "full polyrhythm with log-drum anchor", "shakers and melodic motif only", "djembe answers, shaker rolls"),
    "Hood trap" to FrameworkProfile("Raw Space Frame", "Intro 4 · 3× Verse 16 · 3× Chorus 8 · Outro 4", "minimal riser, tension in piano", "heavy long 808 in open space", "piano only, kit out", "sparse fills, long 808 tails"),
    "Detroit scam-rap pocket" to FrameworkProfile("Scam Rush Frame", "Intro 4 · 3× Verse 16 · 3× Chorus 8 · Outro 4", "hats and claps push ahead of grid", "urgent landing, bending 808 under piano", "piano loop alone, ghost hats", "rushed clap bursts, bent 808 turns"),
    "Florida fast" to FrameworkProfile("Fast Burst Frame", "Intro 4 · 3× Verse 16 · 3× Chorus 8 · Outro 4", "rapid hat build, kick bursts stack", "kick run and short 808 stabs explode", "stabs and tops only", "kick bursts, rapid rolls"),
    "Texas slab" to FrameworkProfile("Slab Drag Frame", "Intro 8 · 3× Verse 16 · 3× Chorus 8 · Outro 8", "slow swell, snare drags behind beat", "sub weight settles, no fast drop", "chopped soul texture alone", "dragged fills, trunk-weight impacts"),
    "Chopped & screwed" to FrameworkProfile("Chop & Drag Frame", "Intro 8 · 3× Verse 16 · 3× Chorus 8 · Outro 8", "riser slowed with echo tails", "slowed landing with stutter-chop edits", "chopped segments alone with wide echo", "stutter-chop edits, dragged rolls"),
    "Drift phonk" to FrameworkProfile("Cowbell Drift Frame", "Intro 8 · 3× Verse 16 · 3× Chorus 8 · Outro 8", "cowbell melody thickens", "saturated 808 slide drifts in", "cowbell and tape hiss up front", "cowbell runs, saturated slides"),
    "Dark trap" to FrameworkProfile("Tension Frame", "Intro 8 · 3× Verse 16 · 3× Chorus 8 · Outro 8", "slow minor riser, restrained hats", "distorted 808 hit in open space", "synth tension and tops only", "distorted hits, tension cuts"),
    "Boom trap" to FrameworkProfile("Swing-Meets-Sub Frame", "Intro 8 · 3× Verse 16 · 3× Chorus 8 · Outro 8", "fill build on the break, trap hats enter", "break and rolling 808 together", "break alone, sub pulled", "swung fills, rolling 808 turns"),
    "Afro-drill" to FrameworkProfile("Log-Drum Glide Frame", "Intro 8 · 3× Verse 16 · 3× Chorus 8 · Outro 8", "log-drum build under dark riser", "808 glide paired with log-drum hit", "log drum and melody only", "glide entries, log-drum answers"),
    "Latin trap" to FrameworkProfile("Tresillo Frame", "Intro 8 · 3× Verse 16 · 3× Chorus 8 · Outro 8", "tresillo 3-3-2 pattern stacks in", "dembow pocket with heavy 808", "melody and tresillo percussion only", "tresillo turns, trap hat rolls"),
    "Amapiano hip-hop" to FrameworkProfile("Log-Drum Anchor Frame", "Intro 8 · 3× Verse 16 · 3× Chorus 8 · Outro 8", "shaker build, log drum anchors", "log-drum line rolls in", "piano and shakers only", "log-drum runs, shaker rolls"),
    "Grime" to FrameworkProfile("Sub Stab Frame", "Intro 4 · 3× Verse 16 · 3× Chorus 8 · Outro 4", "square stabs stack, midrange empty", "sub stab hits in open space", "stabs and tops only", "square sub stabs, syncopated cuts"),
    "New Orleans bounce" to FrameworkProfile("Call-Response Bounce Frame", "Intro 4 · 3× Verse 16 · 3× Chorus 8 · Outro 4", "shaker build, break pattern stacks", "full break bounces in, call answered", "break and shakers only", "call-response chops, shaker runs"),
    "Digicore / glitch trap" to FrameworkProfile("Glitch Maximal Frame", "Intro 4 · 3× Verse 16 · 3× Chorus 8 · Outro 4", "micro-edits stack and stutter up", "bitcrushed weight slams with sparkle", "chiptune sparkle only", "micro-edit fills, bitcrush tails"),
    "Tread" to FrameworkProfile("Full-Speed Frame", "Intro 4 · 3× Verse 16 · 3× Chorus 8 · Outro 4", "break-run build, velocity never lets up", "full speed hit, no half-time relief", "icy synth only, breaks cut dead", "chopped break runs, hard cuts")
  )

  init {
    ensureLoaded()
  }

  fun ensureLoaded() {
    if (initialized) return
    try {
      val jsonString = context.assets.open("bank.json").bufferedReader().use { it.readText() }
      val root = JSONObject(jsonString)

      // Style prompts
      val stylesArray = root.getJSONArray("style_prompts")
      for (i in 0 until stylesArray.length()) {
        val obj = stylesArray.getJSONObject(i)
        val sp = StylePrompt(
          id = obj.optInt("id", i + 1),
          track = obj.optInt("track", 1),
          applicability = obj.optString("applicability", "original"),
          subgenre = obj.optString("subgenre", "Trap"),
          text = obj.optString("text", ""),
          chars = obj.optInt("chars", 0)
        )
        stylePrompts.add(sp)

        if (sp.track != 3 && !verifiedLanes.contains(sp.subgenre)) {
          verifiedLanes.add(sp.subgenre)
        } else if (sp.track == 3 && !universalLanes.contains(sp.subgenre)) {
          universalLanes.add(sp.subgenre)
        }
      }

      // Gospel prompts
      val gospelArray = root.getJSONArray("gospel_prompts")
      for (i in 0 until gospelArray.length()) {
        val obj = gospelArray.getJSONObject(i)
        gospelPrompts.add(
          GospelPrompt(
            id = obj.optInt("id", i + 1),
            group = obj.optString("group", ""),
            applicability = obj.optString("applicability", "preserve"),
            text = obj.optString("text", ""),
            chars = obj.optInt("chars", 0)
          )
        )
      }

      // Slot templates
      val slotsArray = root.getJSONArray("slot_templates")
      for (i in 0 until slotsArray.length()) {
        val obj = slotsArray.getJSONObject(i)
        val sArr = obj.optJSONArray("slots")
        val slotsList = mutableListOf<String>()
        if (sArr != null) {
          for (j in 0 until sArr.length()) slotsList.add(sArr.getString(j))
        }
        slotTemplates.add(
          SlotTemplate(
            id = obj.optInt("id", i + 1),
            applicability = obj.optString("applicability", "preserve"),
            text = obj.optString("text", ""),
            slots = slotsList,
            chars = obj.optInt("chars", 0)
          )
        )
      }

      // Lyrics box templates
      val lyricsArray = root.getJSONArray("lyrics_box_templates")
      for (i in 0 until lyricsArray.length()) {
        val obj = lyricsArray.getJSONObject(i)
        val lt = LyricsTemplate(
          id = obj.optInt("id", i + 1),
          topic = obj.optString("topic", ""),
          variant = obj.optString("variant", "A"),
          applicability = if (obj.optString("variant") == "B") "preserve" else "original",
          text = obj.optString("text", ""),
          chars = obj.optInt("chars", 0)
        )
        lyricsBoxTemplates.add(lt)
        if (!lyricsTopics.contains(lt.topic)) {
          lyricsTopics.add(lt.topic)
        }
      }

      // Exclude banks
      val excludeArray = root.getJSONArray("exclude_banks")
      for (i in 0 until excludeArray.length()) {
        val obj = excludeArray.getJSONObject(i)
        excludeBanks.add(
          ExcludeBank(
            id = "ex_$i",
            name = obj.optString("name", "Exclude $i"),
            text = obj.optString("text", ""),
            chars = obj.optInt("chars", 0)
          )
        )
      }

      // Settings cards
      val settingsArray = root.getJSONArray("settings_cards")
      for (i in 0 until settingsArray.length()) {
        val obj = settingsArray.getJSONObject(i)
        val isPreserve = i == 0
        settingsCards.add(
          SettingsCard(
            id = if (isPreserve) "preserve" else "original",
            title = obj.optString("name", "Card ${i + 1}"),
            route = if (isPreserve) "Extend" else "Create",
            model = "Model v6",
            variety = 0,
            weirdness = if (isPreserve) 25 else 20,
            styleInfluence = if (isPreserve) 70 else 65,
            audioInfluence = if (isPreserve) 80 else 0,
            notes = obj.optString("text", "")
          )
        )
      }

      // Assemble all 41 lanes
      allLanes.addAll(verifiedLanes)
      for (el in expansionLanes) {
        if (!allLanes.contains(el.name)) {
          allLanes.add(el.name)
        }
      }

      initialized = true
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  fun laneDrums(lane: String): String {
    val exp = expansionLanes.find { it.name.equals(lane, ignoreCase = true) }
    if (exp != null) return exp.drumTexture
    return when (lane) {
      "Trap" -> "crisp clap on beat 3, rolling hi-hats with triplet subdivisions, punchy kick locked to tuned 808"
      "Chiraq drill" -> "hard aggressive snare on 3, rapid hi-hat rolls, sliding 808 glides with punchy kick"
      "UK drill" -> "syncopated snare on 3, sliding 808 basslines, rapid triplet hi-hat rolls, reverse cymbals"
      "NY drill" -> "punchy syncopated snare, aggressive hi-hat rolls, sliding 808 glides under sample chop"
      "Boom bap" -> "snapping snare on 2 and 4, dusty swung acoustic drum break, warm round bassline"
      "Boom bap revival / conscious" -> "swung live-pocket drums, warm analog bass support, crisp rimshot"
      "Detroit trap" -> "frantic offbeat claps ahead of the beat, fast rolling hi-hats, bending 808 bass"
      "West Coast / G-funk" -> "heavy kick on 1, fat snare on 2 and 4 with handclaps, thick sine wave bass"
      "Memphis phonk / dark trap" -> "cowbell melody, saturated distorted 808, fast hi-hat triplets, heavy kick"
      "Gospel trap" -> "jubilant trap drums, rolling hi-hats, triumphant 808 supporting B3 organ chords"
      "Cloud rap / dreamo" -> "submerged low-pass drums, floating hi-hats, smooth round 808"
      "Trap soul" -> "laid-back trap drums, crisp finger snaps, warm sub-bass ducking under kick"
      "Jersey club rap" -> "signature 5-beat kick pattern, bed squeak accents, fast energetic break"
      "Plugg" -> "sparse hi-hats, hard crash on 1, short Zaytoven 808 hits, dry snare"
      "Pluggnb" -> "smooth R&B swing, rapid hi-hat rolls, bouncy 808, delicate snaps"
      "Rage" -> "EDM-style punchy kick, hyperactive hi-hat rolls, wall-of-sound clipped 808"
      "Horrorcore" -> "hard-clipped drum hits, aggressive rolls, cavernous cuts, detuned drone bass"
      "Soul-sample hip-hop" -> "swung MPC drum break, warm upright sub support, snapping snare on 2 and 4"
      "Down South / Southern trap" -> "rolling hi-hats, wide lifts, long 808 bass tails, loud rimshot"
      "East Coast" -> "hard snapping snare on 2 and 4, dusty kick, solid sub-bass support"
      "Jazz rap / lo-fi" -> "swung soft brush fills, tape warmth, gentle acoustic kick and snare"
      else -> "tight kick, crisp snare, rolling hi-hats and tuned 808 sub"
    }
  }

  fun buildV6LyricsMap(lane: String, job: JobType): String {
    val drums = laneDrums(lane)
    val fw = frameworks[lane]
    val fwName = if (fw != null) ", framework: ${fw.name}" else ""
    val buildP = if (fw != null) "canvas build — ${fw.build}" else "canvas riser climbs, snare roll builds, hats accelerate"
    val dropP = if (fw != null) "canvas drop — ${fw.drop}" else "canvas drums and 808 at peak spice weight, sample stays lead"
    val breakP = if (fw != null) "canvas breakdown — ${fw.breakdown}" else "uploaded sample and sampled vocals play solo exactly as performed"
    val buildO = if (fw != null) "build — ${fw.build}" else "riser climbs, snare roll builds from 8ths to 32nds, hats accelerate"
    val dropO = if (fw != null) "drop — ${fw.drop}" else "maximum drum and bass weight, hook energy peaks"
    val breakO = if (fw != null) "breakdown — ${fw.breakdown}" else "drums out — melody and tops only, space wide open"

    return if (job == JobType.PRESERVE) {
      """[Session Drummer: $lane preserve build, v6 detailed structure$fwName, $drums, spice only]
($LOCK_LINE — every fill, build, drop, cut, roll and FX move happens in the canvas drums only, sample and sampled vocals untouched throughout)
[Instrumental]
[Intro]
(8 bars: uploaded sample and sampled vocals play exactly as performed, untouched, canvas drums out)
(Last 2 bars: canvas reverse swell and an FX sweep rise quietly underneath, impact sets up the verse drop-in, sample untouched)
[Verse 1]
(16 bars: canvas drums in only — $drums, sample and sampled vocals untouched)
(808 support only, spice only, ducking under the kick, no new melodic layers, canvas hi-hat rolls flick at phrase ends)
(Bar 16: canvas drum fill closes the verse under the sample, sample untouched)
[Build-Up]
(Last 4 bars into the chorus: $buildP, sample and sampled vocals untouched)
(Final bar: canvas cuts for one beat under the sample — stop-time silence — then the drop, sample untouched)
[Chorus 1]
(8 bars: drop on bar 1 — $dropP, sample and sampled vocals untouched)
(Bar 8: canvas stop-time cut — drums stop dead on the last hit, sample carries one beat of air alone)
[Post-Chorus Drop]
(4 bars: heavy canvas drop — 808 and kick carry it, canvas drums switch half-time under the sample, impact on bar 1)
[Verse 2]
(16 bars: canvas drums simplify, pocket clears for the sample, 808 drops to sparse hits, sample untouched)
(Bars 9–10: full canvas drop-out — uploaded sample and sampled vocals play solo exactly as performed)
(Bar 16: canvas hi-hat roll crescendo and a drum fill close the verse, sample untouched)
[Build-Up 2]
(Last 4 bars: canvas riser and reverse swell stack, snare roll rises higher, hats double-time)
[Chorus 2]
(8 bars: full canvas energy returns under the sample, extra canvas tops join, sample untouched)
(Bar 8: canvas drum fill at peak weight, FX sweep throws track forward)
[Breakdown]
(8 bars: all canvas drums out — $breakP)
(Last 2 bars: canvas riser starts climbing quietly under the solo, one reverse swell)
[Final Build-Up]
(4 bars: canvas riser, snare roll and hi-hat rolls stack and climb under the sample, tension at maximum)
(Last beat: canvas cuts — one beat of silence — then the final drop)
[Chorus 3 — Final]
(8 bars: heaviest canvas drop of the track lands on bar 1 under the sample, full drum and 808 weight)
(Bars 5–8: canvas drums switch half-time under the hook, fills at peak)
[Final Drop]
(4 bars: last heavy canvas 808 drop under the sample, half-time weight)
[Outro]
(8 bars: canvas drums strip back layer by layer, uploaded sample and sampled vocals play out alone to a clean finish)
[End]"""
    } else {
      """[Structure: $lane original production, v6 detailed structure$fwName, $drums]
[Instrumental]
[Intro]
(8 bars: main melodic hook and signature texture establish the atmosphere, drums out)
(Last 2 bars: reverse cymbal swell and low-frequency sweep rise quietly, setting up the verse)
[Verse 1]
(16 bars: full drum groove enters — $drums)
(Deep 808 sub bass locks with the kick, hi-hat rolls flick every 4 bars, space left for vocals)
(Bar 16: drum fill and tape-stop cut close the verse)
[Build-Up]
(Last 4 bars into the chorus: $buildO)
(Final bar: full cut on the last beat — stop-time silence — then the drop)
[Chorus 1]
(8 bars: explosive drop on bar 1 — $dropO)
(Bar 8: stop-time snare crack cuts dead, letting the reverb tail ring out)
[Post-Chorus Drop]
(4 bars: heavy half-time switch with sub-bass accentuation, maximum weight)
[Verse 2]
(16 bars: drums strip back to kick and hat, arrangement opens up)
(Bars 9–10: two-bar drop-out, isolated melody only, then full impact drop-in on the one)
(Bar 16: snare roll crescendo and drum fill lead into the build)
[Build-Up 2]
(Last 4 bars: dual risers, accelerating hi-hat triplets, maximum tension)
[Chorus 2]
(8 bars: full energy chorus with additional high-end percussion layers)
[Breakdown]
(8 bars: drums drop out entirely — $breakO)
(Last 2 bars: rising filter sweep re-introduces the energy)
[Final Build-Up]
(4 bars: rapid snare rolls and riser climb together, sub pulses)
[Chorus 3 — Final]
(8 bars: loudest and heaviest chorus of the track, full width and punch)
[Outro]
(8 bars: drums fade out, signature melody plays solo to a clean finish)
[End]"""
    }
  }

  fun getRemixTemplates(lane: String): List<RemixTemplate> {
    val fw = frameworks[lane]
    val drums = laneDrums(lane)
    val families = listOf(
      "Faithful Enhance" to "Preserve the original sample and sampled vocals exactly as performed. Add clean, crisp $lane drums ($drums) underneath only, enhancing dynamic punch while keeping the original vibe 100% intact.",
      "Genre Flip" to "Preserve the original sample and sampled vocals exactly as performed. Completely re-imagine the backdrop as a high-octane $lane groove with $drums, creating a dramatic sonic transformation.",
      "Energy Lift" to "Preserve the original sample and sampled vocals exactly as performed. Accelerate groove intensity with driving $lane percussion, rolling hats, and urgent 808 momentum.",
      "Stripped / Minimal" to "Preserve the original sample and sampled vocals exactly as performed. Strip everything down to sparse, punchy $lane drum hits with maximum space for the sample to breathe.",
      "Heavy Drop Focus" to "Preserve the original sample and sampled vocals exactly as performed. Build immense tension toward colossal, trunk-rattling $lane 808 drops on every hook.",
      "Half-Time Flip" to "Preserve the original sample and sampled vocals exactly as performed. Switch into a heavy, dragging half-time $lane pocket with hard-hitting snare and deep sub.",
      "Bounce Flip" to "Preserve the original sample and sampled vocals exactly as performed. Inject an infectious, syncopated $lane bounce pocket with playful percussion and rhythmic 808 stabs."
    )

    return families.mapIndexed { idx, (fam, prompt) ->
      RemixTemplate(
        id = "remix_${lane}_$idx",
        lane = lane,
        family = fam,
        desc = "$lane · $fam",
        prompt = prompt,
        chars = prompt.length
      )
    }
  }

  /**
   * Resolves BPM bounds, regional category, and catalog tier for any of the 41 studio lanes.
   */
  fun laneTempoProfile(lane: String): LaneTempoProfile {
    val exp = expansionLanes.find { it.name.equals(lane, ignoreCase = true) }
    if (exp != null) {
      val (minBpm, maxBpm) = parseBpmRange(exp.tempoRange)
      return LaneTempoProfile(
        lane = exp.name,
        bpmMin = minBpm,
        bpmMax = maxBpm,
        regionTag = exp.region,
        isVerified = false
      )
    }
    return VERIFIED_TEMPO_PROFILES[lane] ?: LaneTempoProfile(
      lane = lane,
      bpmMin = 120,
      bpmMax = 148,
      regionTag = "Studio Core",
      isVerified = true
    )
  }

  /**
   * Ranks the closest lanes from all 41 lanes for a detected beat BPM.
   */
  fun rankLanesForBpm(bpm: Double, limit: Int = 5): List<LaneTempoMatch> {
    val safeBpm = bpm.coerceIn(50.0, 210.0)
    return allLanes.map { lane ->
      val profile = laneTempoProfile(lane)
      val mid = (profile.bpmMin + profile.bpmMax) / 2.0
      val directDist = when {
        safeBpm < profile.bpmMin -> profile.bpmMin - safeBpm
        safeBpm > profile.bpmMax -> safeBpm - profile.bpmMax
        else -> kotlin.math.abs(safeBpm - mid) * 0.25
      }
      val halfDist = kotlin.math.abs((safeBpm * 0.5) - mid) + 6.0
      val doubleDist = kotlin.math.abs((safeBpm * 2.0) - mid) + 6.0
      val bestScore = minOf(directDist, halfDist, doubleDist)
      val matchNote = when {
        safeBpm in profile.bpmMin.toDouble()..profile.bpmMax.toDouble() -> "Direct BPM Fit (${profile.bpmMin}-${profile.bpmMax})"
        halfDist < directDist -> "Half-Time Fit (${profile.bpmMin}-${profile.bpmMax})"
        doubleDist < directDist -> "Double-Time Fit (${profile.bpmMin}-${profile.bpmMax})"
        else -> "${profile.bpmMin}-${profile.bpmMax} BPM"
      }
      LaneTempoMatch(
        profile = profile,
        distance = bestScore,
        matchNote = matchNote
      )
    }
      .sortedBy { it.distance }
      .take(limit)
  }

  fun matchesBpmBracket(lane: String, bracket: String): Boolean {
    if (bracket == "Any BPM") return true
    val p = laneTempoProfile(lane)
    val mid = (p.bpmMin + p.bpmMax) / 2
    return when (bracket) {
      "< 100" -> p.bpmMin < 100
      "100-128" -> p.bpmMax >= 100 && p.bpmMin <= 128
      "128+" -> p.bpmMax >= 128 || mid >= 128
      else -> true
    }
  }

  companion object {
    private val VERIFIED_TEMPO_PROFILES: Map<String, LaneTempoProfile> = mapOf(
      "Trap" to LaneTempoProfile("Trap", 128, 152, "Atlanta / Core", true),
      "Chiraq drill" to LaneTempoProfile("Chiraq drill", 132, 150, "Chicago", true),
      "UK drill" to LaneTempoProfile("UK drill", 136, 146, "London / UK", true),
      "NY drill" to LaneTempoProfile("NY drill", 138, 152, "New York", true),
      "Boom bap" to LaneTempoProfile("Boom bap", 82, 98, "90s Classic", true),
      "Boom bap revival / conscious" to LaneTempoProfile("Boom bap revival / conscious", 80, 96, "Conscious", true),
      "Detroit trap" to LaneTempoProfile("Detroit trap", 142, 168, "Detroit", true),
      "West Coast / G-funk" to LaneTempoProfile("West Coast / G-funk", 88, 105, "West Coast", true),
      "Memphis phonk / dark trap" to LaneTempoProfile("Memphis phonk / dark trap", 118, 145, "Memphis", true),
      "Gospel trap" to LaneTempoProfile("Gospel trap", 124, 148, "Sanctuary", true),
      "Cloud rap / dreamo" to LaneTempoProfile("Cloud rap / dreamo", 110, 136, "Atmospheric", true),
      "Trap soul" to LaneTempoProfile("Trap soul", 95, 126, "Late Night R&B", true),
      "Jersey club rap" to LaneTempoProfile("Jersey club rap", 134, 150, "Newark / East", true),
      "Plugg" to LaneTempoProfile("Plugg", 128, 148, "Atlanta / Plugg", true),
      "Pluggnb" to LaneTempoProfile("Pluggnb", 122, 144, "Melodic Plugg", true),
      "Rage" to LaneTempoProfile("Rage", 136, 160, "Hyper / Synth", true),
      "Horrorcore" to LaneTempoProfile("Horrorcore", 100, 138, "Dark / Experimental", true),
      "Soul-sample hip-hop" to LaneTempoProfile("Soul-sample hip-hop", 82, 104, "MPC Soul", true),
      "Down South / Southern trap" to LaneTempoProfile("Down South / Southern trap", 120, 144, "South", true),
      "East Coast" to LaneTempoProfile("East Coast", 85, 102, "NYC / East", true),
      "Jazz rap / lo-fi" to LaneTempoProfile("Jazz rap / lo-fi", 74, 94, "Lo-Fi / Jazz", true)
    )

    fun parseBpmRange(rangeStr: String): Pair<Int, Int> {
      val nums = Regex("\\d+").findAll(rangeStr).mapNotNull { it.value.toIntOrNull() }.toList()
      return when {
        nums.size >= 2 -> Pair(nums[0].coerceAtMost(nums[1]), maxOf(nums[0], nums[1]))
        nums.size == 1 -> Pair(nums[0], nums[0] + 15)
        else -> Pair(120, 145)
      }
    }
  }
}

data class LaneTempoProfile(
  val lane: String,
  val bpmMin: Int,
  val bpmMax: Int,
  val regionTag: String,
  val isVerified: Boolean
)

data class LaneTempoMatch(
  val profile: LaneTempoProfile,
  val distance: Double,
  val matchNote: String
)

