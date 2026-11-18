package app.ohrhachaim.core

enum class AppLanguage(val code: String) {
    HE("he"),
    EN("en"),
    ;

    val localeTag: String get() = if (this == HE) "he" else "en"

    companion object {
        fun fromCode(code: String?): AppLanguage = entries.firstOrNull { it.code == code } ?: HE
    }
}

enum class AppearanceChoice(val code: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    ;

    companion object {
        fun fromCode(code: String?): AppearanceChoice = entries.firstOrNull { it.code == code } ?: SYSTEM
    }
}

data class Book(
    val id: Int,
    val heName: String,
    val enName: String,
) {
    fun name(language: AppLanguage): String = if (language == AppLanguage.HE) heName else enName
}

data class Parasha(
    val id: Int,
    val bookId: Int,
    val heName: String,
    val enName: String,
    val sortOrder: Int,
) {
    fun name(language: AppLanguage): String = if (language == AppLanguage.HE) heName else enName
}

data class Comment(
    val id: Long,
    val bookId: Int,
    val parashaId: Int?,
    val chapter: Int,
    val verse: Int,
    val index: Int,
    val dibbur: String,
    val body: String,
    val source: String,
) {
    val isIntroduction: Boolean get() = chapter == 0 || source == "on-your-way-introduction"
}

data class WeeklyReading(
    val date: String,
    val israel: Boolean,
    val hebcalTitle: String,
    val hdate: String,
    val parashaIds: List<Int>,
    val kind: String,
) {
    val id: String get() = "$date#${if (israel) 1 else 0}"
    val isSimchatTorah: Boolean get() = kind == "simchat_torah"
}

data class SearchHit(
    val comment: Comment,
    val snippet: String,
) {
    val id: Long get() = comment.id
}

/** Home destinations. Favourites is opened from the toolbar star, not a home row. */
sealed interface HomeDestination {
    data object Resume : HomeDestination
    data object History : HomeDestination
    data object Weekly : HomeDestination
    data object Search : HomeDestination
    data class Book(val bookId: Int) : HomeDestination
    data object Settings : HomeDestination
    data object Help : HomeDestination
    data object About : HomeDestination
    data object Favourites : HomeDestination
}

sealed interface ReadingRoute {
    val scrollTarget: Long?

    data class Parasha(val id: Int, override val scrollTarget: Long?) : ReadingRoute
    data class Introduction(override val scrollTarget: Long?) : ReadingRoute
    data class Weekly(val ids: List<Int>, override val scrollTarget: Long?) : ReadingRoute
}

/** Israel only when the device region is IL. Anywhere else starts on the diaspora calendar. */
fun usesIsraelCalendar(regionCode: String?): Boolean = regionCode == "IL"
