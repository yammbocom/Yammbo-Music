package it.fast4x.riplay.ui.components.tab

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import it.fast4x.riplay.data.Database
import com.yambo.music.R
import it.fast4x.riplay.enums.PopupType
import it.fast4x.riplay.data.models.Song
import it.fast4x.riplay.ui.components.themed.SmartMessage
import it.fast4x.riplay.utils.appContext
import it.fast4x.riplay.data.models.Album
import it.fast4x.riplay.data.models.Artist
import it.fast4x.riplay.ui.components.tab.toolbar.Descriptive
import it.fast4x.riplay.ui.components.tab.toolbar.MenuIcon
import it.fast4x.riplay.utils.formatAsDuration
import it.fast4x.riplay.utils.getFileNameFromUri
import it.fast4x.riplay.utils.spotify.SpotifyImport
import it.fast4x.riplay.utils.spotify.SpotifyTrack

class ImportSongsFromSpotifyCSV private constructor(
    private val launcher: ManagedActivityResultLauncher<Array<String>, Uri?>
): Descriptive, MenuIcon {

    // Intended to import spotify playlist from csv exported by https://exportify.net https://github.com/pavelkomarov/exportify

    companion object {
        private fun openFile(
            uri: Uri,
            beforeTransaction: (Int, Map<String, String>, String?) -> Unit = { _,_,_ -> },
            afterTransaction: ( Int, Song, Album, List<Artist> ) -> Unit = { _,_,_,_ -> }
        ) {
            val context = appContext()
            val fileName = context.getFileNameFromUri(uri)
            context.applicationContext
                .contentResolver
                .openInputStream(uri)
                ?.use { inputStream ->

                    val rows = csvReader().open(inputStream) { readAllWithHeaderAsSequence().toList() }

                    // Exportify rows name a Spotify track (uri, title, artists, duration), not a
                    // YouTube one: each is matched to a YouTube Music song, as the link import
                    // does. Storing the Spotify uri as the song id left those songs unplayable.
                    if (rows.firstOrNull()?.let(::isExportifyRow) == true) {
                        val name = fileName?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: "Spotify"
                        SpotifyImport.openDialog()
                        SpotifyImport.startFromTracks(name, rows.mapNotNull(::exportifyTrack))
                        return
                    }

                    rows.forEachIndexed { index, row: Map<String, String> ->
                        println("mediaItem index song $index")

                        Database.asyncTransaction {
                            beforeTransaction( index, row, fileName )

                            // Yammbo Music / RiPlay export: the rows carry our own MediaId.
                            val explicitPrefix = if (row["Explicit"] == "true") "e:" else ""
                            val pseudoMediaId = (row["Track Name"]+row["Artist Name(s)"]).filter { it.isLetterOrDigit() }
                            val mediaId = row["MediaId"] ?: pseudoMediaId
                            val title = row["Title"] ?: row["Track Name"] ?: return@asyncTransaction
                            val artistsText = row["Artists"] ?: row["Artist Name(s)"] ?: ""

                            // Tenta prima la colonna "Duration" (testo), poi "Track Duration (ms)"
                            val durationText = row["Duration"] ?: formatAsDuration(row["Track Duration (ms)"]?.toLong() ?: 0L)

                            val song = Song(
                                id = mediaId,
                                title = explicitPrefix+title,
                                artistsText = artistsText,
                                durationText = durationText,
                                thumbnailUrl = row["ThumbnailUrl"] ?: "",
                                totalPlayTimeMs = 1L
                            )

                            val albumId = row["AlbumId"] ?: ""
                            val albumTitle = row["AlbumTitle"]
                            val album = Album(
                                id = albumId,
                                title = albumTitle
                            )

                            val artistNames = row["Artists"]?.split(",")
                            val artistIds = row["ArtistIds"]?.split(",")
                            val mutableArtists = mutableListOf<Artist>()
                            if (artistIds != null && (artistNames?.size == artistIds.size)) {
                                for(idx in artistIds.indices){
                                    val artistName = artistNames.getOrNull(idx)
                                    val artistId = artistIds.getOrNull(idx)
                                    if(artistId!=null){
                                        val artist = Artist(
                                            id = artistId,
                                            name = artistName
                                        )
                                        mutableArtists.add(artist)
                                    }
                                }
                            }

                            afterTransaction( index, song, album, mutableArtists )
                        }
                    }
                }
        }

        /** Exportify.net CSV (any of its versions): Spotify columns and no MediaId of ours. */
        private fun isExportifyRow(row: Map<String, String>): Boolean =
            !row.containsKey("MediaId") &&
                (row.containsKey("Track URI") || row.containsKey("Spotify URI") || row.containsKey("Track Name"))

        private fun exportifyTrack(row: Map<String, String>): SpotifyTrack? {
            val title = row["Track Name"]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val artists = (row["Artist Name(s)"] ?: row["Artist Name"]).orEmpty()
                .split(',').map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", ")
            val duration = (row["Duration (ms)"] ?: row["Track Duration (ms)"])?.trim()?.toLongOrNull() ?: 0L
            val uri = (row["Track URI"] ?: row["Spotify URI"])?.takeIf { it.isNotBlank() }
                ?: "csv:${title.lowercase()}|${artists.lowercase()}"
            return SpotifyTrack(uri = uri, title = title, artists = artists, durationMs = duration)
        }

        @JvmStatic
        @Composable
        fun init(
            beforeTransaction: (Int, Map<String, String>, String?) -> Unit = { _,_,_ ->},
            afterTransaction: ( Int, Song, Album, List<Artist> ) -> Unit = { _,_,_,_ -> }
        ) = ImportSongsFromSpotifyCSV(
            rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri ->
                if( uri == null ) return@rememberLauncherForActivityResult

                openFile( uri, beforeTransaction, afterTransaction )
            }
        )
    }

    override val messageId: Int = R.string.import_playlist
    override val iconId: Int = R.drawable.resource_import
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    override fun onShortClick() {
        try {
            launcher.launch( arrayOf("text/csv", "text/comma-separated-values") )
        } catch (_: ActivityNotFoundException) {
            SmartMessage(
                appContext().resources.getString( R.string.info_not_find_app_open_doc ),
                type = PopupType.Warning, context = appContext()
            )
        }
    }
}
