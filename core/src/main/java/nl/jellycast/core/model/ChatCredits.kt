package nl.jellycast.core.model

/** A person in the credits; [role] is the character for actors. */
data class ChatCredit(val name: String, val role: String? = null)

/** The cast and makers the AI chat is told about. */
data class ChatCredits(
    val actors: List<ChatCredit> = emptyList(),
    val guestStars: List<ChatCredit> = emptyList(),
    val directors: List<ChatCredit> = emptyList(),
    val writers: List<ChatCredit> = emptyList(),
)
