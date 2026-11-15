package com.sameerasw.essentials.launcher

import android.content.Context
import java.util.Locale

data class EmojiEntry(
    val emoji: String,
    val name: String,
    val keywords: List<String>,
    val category: String,
)

object EmojiCatalog {
    private const val PREF_NAME = "komorebi_emoji_catalog"
    private const val KEY_RECENTS = "recent_emojis"
    private const val MAX_RECENTS = 24

    private val ENTRIES: List<EmojiEntry> = listOf(
        // Smileys & Emotion
        EmojiEntry("😀", "Grinning Face", listOf("smile", "happy", "joy"), "Smileys"),
        EmojiEntry("😃", "Grinning Face with Big Eyes", listOf("happy", "smile", "cheerful"), "Smileys"),
        EmojiEntry("😄", "Grinning Face with Smiling Eyes", listOf("happy", "laugh", "smile"), "Smileys"),
        EmojiEntry("😁", "Beaming Face", listOf("grin", "smile", "happy"), "Smileys"),
        EmojiEntry("😅", "Grinning Sweat", listOf("nervous", "relief", "phew", "laugh"), "Smileys"),
        EmojiEntry("😂", "Face with Tears of Joy", listOf("laugh", "lol", "funny", "cry"), "Smileys"),
        EmojiEntry("🤣", "Rolling on the Floor Laughing", listOf("rofl", "lol", "laugh", "haha"), "Smileys"),
        EmojiEntry("😊", "Smiling Face with Smiling Eyes", listOf("blush", "happy", "warm"), "Smileys"),
        EmojiEntry("😇", "Smiling Face with Halo", listOf("angel", "innocent", "good"), "Smileys"),
        EmojiEntry("🙂", "Slightly Smiling Face", listOf("smile", "ok", "fine"), "Smileys"),
        EmojiEntry("🙃", "Upside-Down Face", listOf("sarcasm", "silly", "ironic"), "Smileys"),
        EmojiEntry("😉", "Winking Face", listOf("wink", "flirt", "playful"), "Smileys"),
        EmojiEntry("😌", "Relieved Face", listOf("calm", "peace", "zen"), "Smileys"),
        EmojiEntry("😍", "Smiling Face with Heart-Eyes", listOf("love", "crush", "adore"), "Smileys"),
        EmojiEntry("🥰", "Smiling Face with Hearts", listOf("love", "affection", "warm"), "Smileys"),
        EmojiEntry("😘", "Face Blowing a Kiss", listOf("kiss", "love", "muah"), "Smileys"),
        EmojiEntry("😋", "Face Savoring Food", listOf("yum", "delicious", "tasty"), "Smileys"),
        EmojiEntry("😜", "Winking Face with Tongue", listOf("crazy", "silly", "playful"), "Smileys"),
        EmojiEntry("🤪", "Zany Face", listOf("goofy", "wild", "crazy"), "Smileys"),
        EmojiEntry("🤨", "Face with Raised Eyebrow", listOf("skeptic", "hmm", "sus"), "Smileys"),
        EmojiEntry("🧐", "Face with Monocle", listOf("inspect", "curious", "classy"), "Smileys"),
        EmojiEntry("🤓", "Nerd Face", listOf("geek", "smart", "glasses", "dev"), "Smileys"),
        EmojiEntry("😎", "Smiling Face with Sunglasses", listOf("cool", "chill", "awesome"), "Smileys"),
        EmojiEntry("🤩", "Star-Struck", listOf("wow", "amazed", "excited"), "Smileys"),
        EmojiEntry("🥳", "Partying Face", listOf("party", "celebrate", "birthday"), "Smileys"),
        EmojiEntry("😏", "Smirking Face", listOf("smug", "smirk", "sly"), "Smileys"),
        EmojiEntry("😒", "Unamused Face", listOf("meh", "bored", "annoyed"), "Smileys"),
        EmojiEntry("😞", "Disappointed Face", listOf("sad", "down", "sorry"), "Smileys"),
        EmojiEntry("😔", "Pensive Face", listOf("sad", "thoughtful", "sorry"), "Smileys"),
        EmojiEntry("😟", "Worried Face", listOf("anxious", "concern", "nervous"), "Smileys"),
        EmojiEntry("😕", "Confused Face", listOf("puzzled", "unsure", "huh"), "Smileys"),
        EmojiEntry("🥺", "Pleading Face", listOf("please", "puppy", "cute"), "Smileys"),
        EmojiEntry("😢", "Crying Face", listOf("sad", "tear", "cry"), "Smileys"),
        EmojiEntry("😭", "Loudly Crying Face", listOf("sob", "sad", "cry", "tears"), "Smileys"),
        EmojiEntry("😤", "Face with Steam From Nose", listOf("triumph", "mad", "huff"), "Smileys"),
        EmojiEntry("😠", "Angry Face", listOf("mad", "angry", "upset"), "Smileys"),
        EmojiEntry("😡", "Enraged Face", listOf("furious", "rage", "mad"), "Smileys"),
        EmojiEntry("🤯", "Exploding Head", listOf("mindblown", "wow", "shocked"), "Smileys"),
        EmojiEntry("😳", "Flushed Face", listOf("blush", "embarrassed", "whoa"), "Smileys"),
        EmojiEntry("🥵", "Hot Face", listOf("heat", "sweat", "spicy"), "Smileys"),
        EmojiEntry("🥶", "Cold Face", listOf("freezing", "ice", "winter"), "Smileys"),
        EmojiEntry("😱", "Face Screaming in Fear", listOf("omg", "scared", "shock"), "Smileys"),
        EmojiEntry("😨", "Fearful Face", listOf("scared", "afraid", "nervous"), "Smileys"),
        EmojiEntry("🤔", "Thinking Face", listOf("think", "hmm", "ponder", "idea"), "Smileys"),
        EmojiEntry("🫡", "Saluting Face", listOf("salute", "respect", "yes sir"), "Smileys"),
        EmojiEntry("🤫", "Shushing Face", listOf("quiet", "secret", "shh"), "Smileys"),
        EmojiEntry("🫠", "Melting Face", listOf("melt", "awkward", "heat"), "Smileys"),
        EmojiEntry("🥱", "Yawning Face", listOf("tired", "sleepy", "bored"), "Smileys"),
        EmojiEntry("😴", "Sleeping Face", listOf("sleep", "zzz", "nap"), "Smileys"),
        EmojiEntry("🤐", "Zipper-Mouth Face", listOf("secret", "quiet", "mute"), "Smileys"),
        EmojiEntry("🤢", "Nauseated Face", listOf("sick", "gross", "ill"), "Smileys"),
        EmojiEntry("🤧", "Sneezing Face", listOf("achoo", "cold", "sick"), "Smileys"),
        EmojiEntry("😷", "Face with Medical Mask", listOf("sick", "mask", "health"), "Smileys"),
        EmojiEntry("🤡", "Clown Face", listOf("clown", "silly", "joke"), "Smileys"),
        EmojiEntry("👻", "Ghost", listOf("boo", "spooky", "halloween"), "Smileys"),
        EmojiEntry("💀", "Skull", listOf("dead", "skeleton", "lol"), "Smileys"),
        EmojiEntry("👽", "Alien", listOf("ufo", "space", "et"), "Smileys"),
        EmojiEntry("🤖", "Robot", listOf("bot", "ai", "android", "tech"), "Smileys"),
        EmojiEntry("💩", "Pile of Poo", listOf("poop", "crap", "funny"), "Smileys"),

        // Gestures & People
        EmojiEntry("👍", "Thumbs Up", listOf("like", "yes", "ok", "approve", "good"), "Gestures"),
        EmojiEntry("👎", "Thumbs Down", listOf("dislike", "no", "bad"), "Gestures"),
        EmojiEntry("👌", "OK Hand", listOf("ok", "perfect", "nice"), "Gestures"),
        EmojiEntry("🤌", "Pinched Fingers", listOf("chef kiss", "italian", "perfection"), "Gestures"),
        EmojiEntry("✌️", "Victory Hand", listOf("peace", "two", "v"), "Gestures"),
        EmojiEntry("🤞", "Crossed Fingers", listOf("luck", "hope", "wish"), "Gestures"),
        EmojiEntry("🤟", "Love-You Gesture", listOf("love", "rock", "ily"), "Gestures"),
        EmojiEntry("🤘", "Sign of the Horns", listOf("rock", "metal", "awesome"), "Gestures"),
        EmojiEntry("🤙", "Call Me Hand", listOf("shaka", "hang loose", "call"), "Gestures"),
        EmojiEntry("👋", "Waving Hand", listOf("hi", "hello", "bye", "wave"), "Gestures"),
        EmojiEntry("🤚", "Raised Back of Hand", listOf("stop", "hand", "highfive"), "Gestures"),
        EmojiEntry("👏", "Clapping Hands", listOf("clap", "bravo", "applause", "congrats"), "Gestures"),
        EmojiEntry("🙌", "Raising Hands", listOf("hooray", "celebrate", "praise"), "Gestures"),
        EmojiEntry("🫶", "Heart Hands", listOf("love", "heart", "care"), "Gestures"),
        EmojiEntry("🤝", "Handshake", listOf("deal", "agree", "partner"), "Gestures"),
        EmojiEntry("🙏", "Folded Hands", listOf("please", "thank you", "thanks", "pray"), "Gestures"),
        EmojiEntry("💪", "Flexed Biceps", listOf("strong", "muscle", "gym", "power"), "Gestures"),
        EmojiEntry("✍️", "Writing Hand", listOf("write", "note", "edit", "sign"), "Gestures"),
        EmojiEntry("👀", "Eyes", listOf("look", "see", "watch", "peeking"), "Gestures"),
        EmojiEntry("🧠", "Brain", listOf("smart", "think", "mind", "ai"), "Gestures"),
        EmojiEntry("🤷", "Person Shrugging", listOf("shrug", "idk", "dunno", "whatever"), "Gestures"),
        EmojiEntry("🤦", "Person Facepalming", listOf("facepalm", "duh", "oops"), "Gestures"),
        EmojiEntry("🙋", "Person Raising Hand", listOf("question", "hi", "me"), "Gestures"),
        EmojiEntry("🫂", "People Hugging", listOf("hug", "comfort", "care"), "Gestures"),

        // Hearts & Symbols
        EmojiEntry("❤️", "Red Heart", listOf("love", "heart", "like"), "Symbols"),
        EmojiEntry("🧡", "Orange Heart", listOf("love", "heart", "orange"), "Symbols"),
        EmojiEntry("💛", "Yellow Heart", listOf("love", "heart", "yellow"), "Symbols"),
        EmojiEntry("💚", "Green Heart", listOf("love", "heart", "green"), "Symbols"),
        EmojiEntry("💙", "Blue Heart", listOf("love", "heart", "blue"), "Symbols"),
        EmojiEntry("💜", "Purple Heart", listOf("love", "heart", "purple"), "Symbols"),
        EmojiEntry("🖤", "Black Heart", listOf("love", "heart", "dark"), "Symbols"),
        EmojiEntry("🤍", "White Heart", listOf("love", "heart", "pure"), "Symbols"),
        EmojiEntry("💔", "Broken Heart", listOf("sad", "heartbreak"), "Symbols"),
        EmojiEntry("💖", "Sparkling Heart", listOf("love", "sparkle", "pink"), "Symbols"),
        EmojiEntry("💯", "Hundred Points", listOf("100", "perfect", "score"), "Symbols"),
        EmojiEntry("🔥", "Fire", listOf("hot", "lit", "flame", "trending"), "Symbols"),
        EmojiEntry("✨", "Sparkles", listOf("magic", "clean", "new", "ai", "shine"), "Symbols"),
        EmojiEntry("⚡", "High Voltage", listOf("lightning", "fast", "bolt", "power", "zap"), "Symbols"),
        EmojiEntry("💥", "Collision", listOf("boom", "bang", "crash"), "Symbols"),
        EmojiEntry("💫", "Dizzy", listOf("star", "shoot", "spark"), "Symbols"),
        EmojiEntry("⭐", "Star", listOf("favorite", "rating", "gold"), "Symbols"),
        EmojiEntry("🌟", "Glowing Star", listOf("shine", "sparkle", "great"), "Symbols"),
        EmojiEntry("✅", "Check Mark Button", listOf("done", "yes", "pass", "ok", "check"), "Symbols"),
        EmojiEntry("☑️", "Check Box with Check", listOf("todo", "task", "checked"), "Symbols"),
        EmojiEntry("❌", "Cross Mark", listOf("no", "wrong", "fail", "error", "close"), "Symbols"),
        EmojiEntry("⚠️", "Warning", listOf("alert", "caution", "danger"), "Symbols"),
        EmojiEntry("🚫", "Prohibited", listOf("ban", "block", "stop", "no"), "Symbols"),
        EmojiEntry("❓", "Question Mark", listOf("help", "ask", "what"), "Symbols"),
        EmojiEntry("❗", "Exclamation Mark", listOf("important", "alert", "bang"), "Symbols"),
        EmojiEntry("💡", "Light Bulb", listOf("idea", "tip", "bright", "smart"), "Symbols"),
        EmojiEntry("🔔", "Bell", listOf("notify", "alert", "ring"), "Symbols"),
        EmojiEntry("🔕", "Bell with Slash", listOf("mute", "silent", "dnd"), "Symbols"),
        EmojiEntry("🎵", "Musical Note", listOf("music", "song", "audio"), "Symbols"),
        EmojiEntry("🎶", "Musical Notes", listOf("music", "melody", "sound"), "Symbols"),
        EmojiEntry("♻️", "Recycling Symbol", listOf("recycle", "green", "reuse"), "Symbols"),
        EmojiEntry("➕", "Plus", listOf("add", "more", "new"), "Symbols"),
        EmojiEntry("➖", "Minus", listOf("subtract", "remove"), "Symbols"),
        EmojiEntry("➡️", "Right Arrow", listOf("next", "arrow", "forward"), "Symbols"),
        EmojiEntry("⬅️", "Left Arrow", listOf("back", "arrow", "previous"), "Symbols"),
        EmojiEntry("⬆️", "Up Arrow", listOf("up", "arrow", "top"), "Symbols"),
        EmojiEntry("⬇️", "Down Arrow", listOf("down", "arrow", "bottom"), "Symbols"),

        // Animals & Nature
        EmojiEntry("🐶", "Dog Face", listOf("puppy", "pet", "woof"), "Animals"),
        EmojiEntry("🐱", "Cat Face", listOf("kitty", "pet", "meow"), "Animals"),
        EmojiEntry("🦊", "Fox", listOf("fox", "clever", "orange"), "Animals"),
        EmojiEntry("🐻", "Bear", listOf("bear", "animal"), "Animals"),
        EmojiEntry("🐼", "Panda", listOf("panda", "cute", "bear"), "Animals"),
        EmojiEntry("🐨", "Koala", listOf("koala", "cute"), "Animals"),
        EmojiEntry("🦁", "Lion", listOf("lion", "king", "roar"), "Animals"),
        EmojiEntry("🐸", "Frog", listOf("frog", "pepe", "toad"), "Animals"),
        EmojiEntry("🐧", "Penguin", listOf("linux", "bird", "cold"), "Animals"),
        EmojiEntry("🦉", "Owl", listOf("owl", "night", "wise"), "Animals"),
        EmojiEntry("🦅", "Eagle", listOf("bird", "fly"), "Animals"),
        EmojiEntry("🐝", "Honeybee", listOf("bee", "honey", "buzz"), "Animals"),
        EmojiEntry("🦋", "Butterfly", listOf("butterfly", "nature"), "Animals"),
        EmojiEntry("🐛", "Bug", listOf("bug", "issue", "debug", "insect"), "Animals"),
        EmojiEntry("🐢", "Turtle", listOf("slow", "shell"), "Animals"),
        EmojiEntry("🐙", "Octopus", listOf("octopus", "sea", "github"), "Animals"),
        EmojiEntry("🐳", "Spouting Whale", listOf("whale", "docker", "ocean"), "Animals"),
        EmojiEntry("🦄", "Unicorn", listOf("magic", "startup", "fantasy"), "Animals"),
        EmojiEntry("🌸", "Cherry Blossom", listOf("sakura", "flower", "spring", "komorebi"), "Animals"),
        EmojiEntry("🌿", "Herb", listOf("leaf", "plant", "green", "nature"), "Animals"),
        EmojiEntry("🌳", "Deciduous Tree", listOf("tree", "forest", "komorebi"), "Animals"),
        EmojiEntry("☀️", "Sun", listOf("sunny", "weather", "bright", "day"), "Animals"),
        EmojiEntry("🌙", "Crescent Moon", listOf("night", "dark", "sleep"), "Animals"),
        EmojiEntry("⛅", "Sun Behind Cloud", listOf("weather", "cloudy"), "Animals"),
        EmojiEntry("🌧️", "Cloud with Rain", listOf("rain", "weather"), "Animals"),
        EmojiEntry("❄️", "Snowflake", listOf("snow", "cold", "winter"), "Animals"),
        EmojiEntry("🌈", "Rainbow", listOf("colors", "pride", "sky"), "Animals"),
        EmojiEntry("🌊", "Water Wave", listOf("ocean", "sea", "surf"), "Animals"),

        // Food & Drink
        EmojiEntry("☕", "Hot Beverage", listOf("coffee", "tea", "cafe", "morning"), "Food"),
        EmojiEntry("🍵", "Teacup Without Handle", listOf("matcha", "green tea", "tea"), "Food"),
        EmojiEntry("🧋", "Bubble Tea", listOf("boba", "milk tea"), "Food"),
        EmojiEntry("🍺", "Beer Mug", listOf("beer", "cheers", "drink"), "Food"),
        EmojiEntry("🍷", "Wine Glass", listOf("wine", "drink"), "Food"),
        EmojiEntry("🍕", "Pizza", listOf("pizza", "slice", "food"), "Food"),
        EmojiEntry("🍔", "Hamburger", listOf("burger", "fast food"), "Food"),
        EmojiEntry("🍟", "French Fries", listOf("fries", "snack"), "Food"),
        EmojiEntry("🌮", "Taco", listOf("taco", "mexican"), "Food"),
        EmojiEntry("🍣", "Sushi", listOf("sushi", "japanese"), "Food"),
        EmojiEntry("🍜", "Steaming Bowl", listOf("ramen", "noodles", "soup"), "Food"),
        EmojiEntry("🍙", "Rice Ball", listOf("onigiri", "rice"), "Food"),
        EmojiEntry("🍰", "Shortcake", listOf("cake", "dessert", "sweet"), "Food"),
        EmojiEntry("🎂", "Birthday Cake", listOf("birthday", "party", "cake"), "Food"),
        EmojiEntry("🍪", "Cookie", listOf("cookie", "snack"), "Food"),
        EmojiEntry("🍩", "Doughnut", listOf("donut", "sweet"), "Food"),
        EmojiEntry("🍎", "Red Apple", listOf("apple", "fruit"), "Food"),
        EmojiEntry("🍌", "Banana", listOf("banana", "fruit"), "Food"),
        EmojiEntry("🍓", "Strawberry", listOf("berry", "fruit"), "Food"),
        EmojiEntry("🥑", "Avocado", listOf("avocado", "guac"), "Food"),

        // Activities & Celebration
        EmojiEntry("🎉", "Party Popper", listOf("celebrate", "congrats", "tada", "party"), "Activities"),
        EmojiEntry("🎊", "Confetti Ball", listOf("party", "celebrate"), "Activities"),
        EmojiEntry("🏆", "Trophy", listOf("win", "award", "champion", "first"), "Activities"),
        EmojiEntry("🥇", "1st Place Medal", listOf("gold", "winner", "first"), "Activities"),
        EmojiEntry("🎯", "Bullseye", listOf("target", "goal", "focus"), "Activities"),
        EmojiEntry("🎮", "Video Game", listOf("gaming", "controller", "play"), "Activities"),
        EmojiEntry("🎲", "Game Die", listOf("dice", "random", "luck"), "Activities"),
        EmojiEntry("🎨", "Artist Palette", listOf("art", "design", "color", "paint"), "Activities"),
        EmojiEntry("🎬", "Clapper Board", listOf("movie", "film", "video"), "Activities"),
        EmojiEntry("🎧", "Headphone", listOf("music", "audio", "listen"), "Activities"),
        EmojiEntry("⚽", "Soccer Ball", listOf("football", "sport"), "Activities"),
        EmojiEntry("🏀", "Basketball", listOf("sport", "nba"), "Activities"),
        EmojiEntry("🏃", "Person Running", listOf("run", "sprint", "exercise"), "Activities"),

        // Travel & Places
        EmojiEntry("🚀", "Rocket", listOf("launch", "ship", "fast", "space", "deploy"), "Travel"),
        EmojiEntry("✈️", "Airplane", listOf("flight", "travel", "plane"), "Travel"),
        EmojiEntry("🚗", "Automobile", listOf("car", "drive"), "Travel"),
        EmojiEntry("🚆", "Train", listOf("rail", "transit"), "Travel"),
        EmojiEntry("🚲", "Bicycle", listOf("bike", "cycle"), "Travel"),
        EmojiEntry("🏠", "House", listOf("home", "house"), "Travel"),
        EmojiEntry("🏢", "Office Building", listOf("work", "office"), "Travel"),
        EmojiEntry("🗺️", "World Map", listOf("map", "travel", "world"), "Travel"),
        EmojiEntry("🌍", "Globe Showing Europe-Africa", listOf("earth", "world", "global"), "Travel"),
        EmojiEntry("⛰️", "Mountain", listOf("hike", "peak", "nature"), "Travel"),
        EmojiEntry("🏝️", "Desert Island", listOf("beach", "vacation"), "Travel"),

        // Objects & Tech
        EmojiEntry("💻", "Laptop", listOf("computer", "code", "dev", "mac", "pc"), "Objects"),
        EmojiEntry("📱", "Mobile Phone", listOf("phone", "android", "smartphone", "device"), "Objects"),
        EmojiEntry("⌨️", "Keyboard", listOf("type", "keys", "input"), "Objects"),
        EmojiEntry("🖥️", "Desktop Computer", listOf("monitor", "screen", "pc"), "Objects"),
        EmojiEntry("⚙️", "Gear", listOf("settings", "config", "preferences", "cog"), "Objects"),
        EmojiEntry("🔧", "Wrench", listOf("fix", "tool", "repair"), "Objects"),
        EmojiEntry("🔨", "Hammer", listOf("build", "tool"), "Objects"),
        EmojiEntry("🛠️", "Hammer and Wrench", listOf("tools", "build", "fix", "dev"), "Objects"),
        EmojiEntry("🧪", "Test Tube", listOf("test", "experiment", "lab", "science"), "Objects"),
        EmojiEntry("🔬", "Microscope", listOf("inspect", "research", "science"), "Objects"),
        EmojiEntry("🔍", "Magnifying Glass", listOf("search", "find", "zoom"), "Objects"),
        EmojiEntry("🔑", "Key", listOf("password", "auth", "token", "secret"), "Objects"),
        EmojiEntry("🔒", "Locked", listOf("lock", "secure", "private", "security"), "Objects"),
        EmojiEntry("🔓", "Unlocked", listOf("unlock", "open"), "Objects"),
        EmojiEntry("🔗", "Link", listOf("url", "chain", "hyperlink"), "Objects"),
        EmojiEntry("📎", "Paperclip", listOf("attach", "file"), "Objects"),
        EmojiEntry("📋", "Clipboard", listOf("copy", "paste", "notes"), "Objects"),
        EmojiEntry("📌", "Pushpin", listOf("pin", "save", "bookmark"), "Objects"),
        EmojiEntry("📝", "Memo", listOf("note", "write", "edit", "text"), "Objects"),
        EmojiEntry("📄", "Page Facing Up", listOf("doc", "document", "file"), "Objects"),
        EmojiEntry("📁", "File Folder", listOf("folder", "directory"), "Objects"),
        EmojiEntry("📅", "Calendar", listOf("date", "schedule", "event"), "Objects"),
        EmojiEntry("⏰", "Alarm Clock", listOf("time", "alarm", "timer", "clock"), "Objects"),
        EmojiEntry("⏱️", "Stopwatch", listOf("timer", "fast", "benchmark"), "Objects"),
        EmojiEntry("⏳", "Hourglass", listOf("wait", "loading", "time"), "Objects"),
        EmojiEntry("📦", "Package", listOf("box", "npm", "bundle", "ship"), "Objects"),
        EmojiEntry("🗑️", "Wastebasket", listOf("trash", "delete", "remove"), "Objects"),
        EmojiEntry("📧", "E-Mail", listOf("mail", "email", "message"), "Objects"),
        EmojiEntry("💬", "Speech Balloon", listOf("chat", "comment", "message"), "Objects"),
        EmojiEntry("📢", "Loudspeaker", listOf("announce", "broadcast"), "Objects"),
        EmojiEntry("🔋", "Battery", listOf("power", "charge"), "Objects"),
        EmojiEntry("🔦", "Flashlight", listOf("torch", "light"), "Objects"),
        EmojiEntry("📷", "Camera", listOf("photo", "picture"), "Objects"),
        EmojiEntry("🎥", "Movie Camera", listOf("video", "record"), "Objects"),
        EmojiEntry("💰", "Money Bag", listOf("cash", "money", "rich", "dollar"), "Objects"),
        EmojiEntry("💳", "Credit Card", listOf("pay", "card"), "Objects"),
        EmojiEntry("📚", "Books", listOf("read", "study", "docs", "library"), "Objects"),
        EmojiEntry("🔖", "Bookmark", listOf("save", "tag"), "Objects"),
        EmojiEntry("🏷️", "Label", listOf("tag", "price", "badge"), "Objects"),
        EmojiEntry("🚩", "Triangular Flag", listOf("flag", "report", "milestone"), "Objects"),
        EmojiEntry("🏁", "Chequered Flag", listOf("finish", "done", "race"), "Objects"),
    )

    fun all(): List<EmojiEntry> = ENTRIES

    fun categories(): List<String> = ENTRIES.map { it.category }.distinct()

    fun search(query: String): List<EmojiEntry> {
        val cleaned = query.trim().removePrefix(":").removeSuffix(":").lowercase(Locale.US)
        if (cleaned.isEmpty()) return ENTRIES

        return ENTRIES.mapNotNull { entry ->
            val nameLower = entry.name.lowercase(Locale.US)
            val catLower = entry.category.lowercase(Locale.US)
            val score = when {
                entry.emoji == cleaned -> 100
                nameLower == cleaned -> 95
                entry.keywords.any { it.equals(cleaned, ignoreCase = true) } -> 90
                nameLower.startsWith(cleaned) -> 80
                entry.keywords.any { it.lowercase(Locale.US).startsWith(cleaned) } -> 70
                nameLower.contains(cleaned) -> 60
                entry.keywords.any { it.lowercase(Locale.US).contains(cleaned) } -> 50
                catLower.contains(cleaned) -> 30
                else -> 0
            }
            if (score > 0) entry to score else null
        }.sortedByDescending { it.second }.map { it.first }
    }

    fun getRecents(context: Context): List<EmojiEntry> {
        val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_RECENTS, null) ?: return emptyList()
        val emojis = raw.split("|").filter { it.isNotBlank() }
        val byEmoji = ENTRIES.associateBy { it.emoji }
        return emojis.mapNotNull { symbol ->
            byEmoji[symbol] ?: EmojiEntry(symbol, symbol, emptyList(), "Recent")
        }
    }

    fun recordUsage(context: Context, emoji: String) {
        val clean = emoji.trim()
        if (clean.isEmpty()) return
        val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val existing = (prefs.getString(KEY_RECENTS, null)?.split("|") ?: emptyList())
            .filter { it.isNotBlank() && it != clean }
        val updated = (listOf(clean) + existing).take(MAX_RECENTS)
        prefs.edit().putString(KEY_RECENTS, updated.joinToString("|")).apply()
    }
}
