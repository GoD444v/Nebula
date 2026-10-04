package nebula.music.data.models

/**
 * One card on the Home feed. YouTube Music mixes songs, albums, playlists and mood
 * buttons in the same shelves, so a card carries both kinds of target.
 */
data class HomeCard(
    val title: String,
    val subtitle: String = "",
    val thumbnailUrl: String = "",
    /** Set when the card is a song that can be played straight away. */
    val videoId: String = "",
    /** Set for playlist / album / artist cards: where the card opens. */
    val browseId: String = "",
    val params: String = "",
    /** MUSIC_PAGE_TYPE_* when known; consumed by search grouping. */
    val pageType: String = ""
) {
    val isSong: Boolean get() = videoId.isNotBlank()
}

/** A titled row of cards, e.g. "Trending" or "New albums and singles". */
data class HomeSection(
    val title: String,
    val cards: List<HomeCard>
)

/** A filter chip above the feed. Selecting one reloads the feed with its params. */
data class HomeChip(
    val label: String,
    val params: String
)

data class HomeFeed(
    val chips: List<HomeChip>,
    val sections: List<HomeSection>
)
