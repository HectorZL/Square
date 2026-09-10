package dev.lelonio.square.data

import android.util.Base64
import dev.lelonio.square.ui.MainViewModel.ArtistPage
import dev.lelonio.square.ui.MainViewModel.ArtistRelease
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * Resolves artist top tracks, albums, singles, followers, and monthly listeners
 * directly from Spotify's web player artist entity.
 *
 * Spotify's public Web API rate-limits librespot's shared client ID (HTTP 429)
 * for users without a custom developer app. Spotify's web player CDN renders
 * the full artist entity into the page HTML without authentication or API quota limits,
 * providing the top 5 tracks, all albums, singles/EPs, monthly listeners, and bio.
 */
object SpotifyWebArtist {
    private const val TAG = "SpotifyWebArtist"
    private val scriptRegex = Regex("<script[^>]*>(.*?)</script>", RegexOption.DOT_MATCHES_ALL)

    suspend fun fetch(
        artistId: String,
        client: OkHttpClient,
    ): ArtistPage? = withContext(Dispatchers.IO) {
        val url = "https://open.spotify.com/artist/$artistId"
        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            )
            .header("Accept-Language", java.util.Locale.getDefault().language)
            .build()

        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    android.util.Log.w(TAG, "HTTP ${response.code} for artist $artistId")
                    return@withContext null
                }
                val html = response.body?.string() ?: return@withContext null
                parseHtml(html, artistId)
            }
        }.onFailure {
            android.util.Log.w(TAG, "Failed to load web artist $artistId: ${it.message}")
        }.getOrNull()
    }

    fun parseHtml(html: String, artistId: String): ArtistPage? {
        val matches = scriptRegex.findAll(html)
        for (match in matches) {
            val content = match.groupValues[1].trim()
            if (content.length < 500 || !content.startsWith("eyJ")) continue

            val decoded = runCatching {
                val bytes = Base64.decode(content, Base64.DEFAULT)
                String(bytes, Charsets.UTF_8)
            }.getOrNull() ?: continue

            if (!decoded.contains(artistId)) continue

            val root = runCatching { JSONObject(decoded) }.getOrNull() ?: continue
            val items = root.optJSONObject("entities")?.optJSONObject("items") ?: continue
            val artistKey = "spotify:artist:$artistId"
            val artistData = items.optJSONObject(artistKey)
                ?: items.keys().asSequence().firstOrNull { it.startsWith("spotify:artist:") }?.let { items.optJSONObject(it) }
                ?: continue

            return parseArtistData(artistData, artistId)
        }
        return null
    }

    private fun parseArtistData(data: JSONObject, artistId: String): ArtistPage {
        val profile = data.optJSONObject("profile")
        val name = profile?.optString("name").orEmpty()
        val biography = profile?.optJSONObject("biography")?.optString("text")

        val stats = data.optJSONObject("stats")
        val followersCount = stats?.optInt("followers") ?: 0
        val monthlyListenersCount = stats?.optLong("monthlyListeners") ?: 0L

        val monthlyListenersFormatted = if (monthlyListenersCount > 0) {
            when {
                monthlyListenersCount >= 1_000_000 -> String.format(
                    java.util.Locale.US,
                    "%.1f M oyentes mensuales",
                    monthlyListenersCount / 1_000_000.0,
                )
                monthlyListenersCount >= 1_000 -> String.format(
                    java.util.Locale.US,
                    "%.1f K oyentes mensuales",
                    monthlyListenersCount / 1_000.0,
                )
                else -> "$monthlyListenersCount oyentes mensuales"
            }
        } else null

        val visuals = data.optJSONObject("visuals")
        val avatarUrl = extractBestImage(visuals?.optJSONObject("avatarImage")?.optJSONArray("sources"), 320)

        val disco = data.optJSONObject("discography")

        // Parse top tracks (strictly top 5)
        val topTracksArray = disco?.optJSONObject("topTracks")?.optJSONArray("items")
        val tracks = mutableListOf<CatalogTrack>()
        if (topTracksArray != null) {
            for (i in 0 until topTracksArray.length()) {
                val item = topTracksArray.optJSONObject(i) ?: continue
                val track = item.optJSONObject("track") ?: continue
                val uri = track.optString("uri")
                if (!uri.startsWith("spotify:track:")) continue

                val trackName = track.optString("name")
                val albumObj = track.optJSONObject("albumOfTrack")
                val albumName = albumObj?.optString("name").orEmpty()
                val coverArt = extractBestImage(albumObj?.optJSONObject("coverArt")?.optJSONArray("sources"), 300)

                val artistsArray = track.optJSONObject("artists")?.optJSONArray("items")
                val credited = mutableListOf<CatalogArtist>()
                if (artistsArray != null) {
                    for (j in 0 until artistsArray.length()) {
                        val aObj = artistsArray.optJSONObject(j) ?: continue
                        val aName = aObj.optJSONObject("profile")?.optString("name").orEmpty()
                        if (aName.isNotEmpty()) {
                            credited.add(CatalogArtist(aName, aObj.optString("uri").takeIf { it.isNotEmpty() }))
                        }
                    }
                }

                val explicit = track.optJSONObject("contentRating")?.optString("label") == "EXPLICIT"

                tracks.add(
                    CatalogTrack(
                        uri = uri,
                        name = trackName,
                        artist = credited.joinToString(", ") { it.name },
                        artistUri = credited.firstOrNull()?.uri,
                        artists = credited,
                        album = albumName,
                        durationMs = 0L,
                        explicit = explicit,
                        artworkUrl = coverArt,
                    ),
                )
            }
        }

        fun extractReleases(sectionKey: String): List<SearchItem> {
            val section = disco?.optJSONObject(sectionKey)
            val items = section?.optJSONArray("items") ?: return emptyList()
            val list = mutableListOf<SearchItem>()
            for (i in 0 until items.length()) {
                val group = items.optJSONObject(i) ?: continue
                val releases = group.optJSONObject("releases")?.optJSONArray("items") ?: continue
                for (j in 0 until releases.length()) {
                    val rel = releases.optJSONObject(j) ?: continue
                    val uri = rel.optString("uri")
                    if (!uri.startsWith("spotify:album:")) continue

                    val title = rel.optString("name")
                    val type = rel.optString("type")
                    val year = rel.optJSONObject("date")?.optInt("year")?.takeIf { it > 0 }?.toString()
                        ?: rel.optString("date").take(4)
                    val label = when (type) {
                        "SINGLE" -> "Sencillo"
                        "ALBUM" -> "Álbum"
                        "COMPILATION" -> "Recopilación"
                        else -> ""
                    }
                    val subtitle = if (label.isNotEmpty() && year.isNotEmpty()) "$label • $year" else year.ifEmpty { label }
                    val cover = extractBestImage(rel.optJSONObject("coverArt")?.optJSONArray("sources"), 300)

                    list.add(
                        SearchItem(
                            uri = uri,
                            title = title,
                            subtitle = subtitle,
                            artworkUrl = cover,
                        ),
                    )
                }
            }
            return list.distinctBy { it.title.lowercase() }.sortedByDescending { it.subtitle }
        }

        val albumsList = extractReleases("albums") + extractReleases("compilations")
        val singlesList = extractReleases("singles")

        val latestObj = disco?.optJSONObject("latest")
        val latestRelease = if (latestObj != null) {
            val lUri = latestObj.optString("uri")
            if (lUri.startsWith("spotify:album:")) {
                val lTitle = latestObj.optString("name")
                val lDate = latestObj.optJSONObject("date")?.optInt("year")?.takeIf { it > 0 }?.toString()
                    ?: latestObj.optString("date").take(4)
                val lCover = extractBestImage(latestObj.optJSONObject("coverArt")?.optJSONArray("sources"), 300)
                val totalCount = latestObj.optJSONObject("tracks")?.optInt("totalCount") ?: 1
                ArtistRelease(
                    uri = lUri,
                    title = lTitle,
                    artworkUrl = lCover,
                    releaseDate = lDate,
                    trackCount = totalCount,
                )
            } else null
        } else null

        return ArtistPage(
            playlists = emptyList(),
            tracks = tracks.take(5),
            latest = latestRelease,
            albums = albumsList,
            singles = singlesList,
            appearsOn = emptyList(),
            relatedArtists = emptyList(),
            followers = followersCount,
            genres = emptyList(),
            monthlyListeners = monthlyListenersFormatted,
            name = name.takeIf { it.isNotEmpty() },
            artworkUrl = avatarUrl,
            biography = biography,
        )
    }

    private fun extractBestImage(sources: JSONArray?, preferredWidth: Int): String? {
        if (sources == null || sources.length() == 0) return null
        var fallback: String? = null
        for (i in 0 until sources.length()) {
            val src = sources.optJSONObject(i) ?: continue
            val url = src.optString("url").takeIf { it.isNotEmpty() } ?: continue
            val width = src.optInt("width")
            if (width in (preferredWidth - 40)..(preferredWidth + 60)) return url
            if (fallback == null) fallback = url
        }
        return fallback
    }
}
