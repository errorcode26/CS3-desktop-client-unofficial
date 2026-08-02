package dev.akash.skystream

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.util.Base64
import android.util.Log
import androidx.core.net.toUri
import androidx.tvprovider.media.tv.Channel
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat

const val PROGRAM_ID_LIST_KEY = "persistent_program_ids"
const val APP_STRING_SHARE = "csshare"

object TvChannelUtils {

    private fun Context.getPrefs() = getSharedPreferences("tv_channel_utils", Context.MODE_PRIVATE)

    fun Context.saveProgramId(programId: Long) {
        val existing = getStoredProgramIds().toMutableSet()
        existing.add(programId)
        getPrefs().edit().putStringSet(PROGRAM_ID_LIST_KEY, existing.map { it.toString() }.toSet()).apply()
    }

    fun Context.getStoredProgramIds(): List<Long> {
        return getPrefs().getStringSet(PROGRAM_ID_LIST_KEY, emptySet())?.mapNotNull { it.toLongOrNull() } ?: emptyList()
    }

    fun Context.removeProgramId(programId: Long) {
         val existing = getStoredProgramIds().toMutableSet()
         existing.remove(programId)
         getPrefs().edit().putStringSet(PROGRAM_ID_LIST_KEY, existing.map { it.toString() }.toSet()).apply()
    }

    fun getChannelId(context: Context, channelName: String): Long? {
        return try {
            context.contentResolver.query(
                TvContractCompat.Channels.CONTENT_URI,
                arrayOf(
                    TvContractCompat.Channels._ID,
                    TvContractCompat.Channels.COLUMN_DISPLAY_NAME
                ),
                null,
                null,
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(
                        cursor.getColumnIndexOrThrow(TvContractCompat.Channels._ID)
                    )
                    val name = cursor.getString(
                        cursor.getColumnIndexOrThrow(TvContractCompat.Channels.COLUMN_DISPLAY_NAME)
                    )
                    if (name == channelName) return id
                }
                null
            }
        } catch (e: Exception) {
            Log.e("TvChannelUtils", "Query failed: ${e.message}", e)
            null
        }
    }

    /** Insert programs into a channel 
     * Items are Maps acting as the SearchResponse object
     */
    @SuppressLint("RestrictedApi")
    fun addPrograms(context: Context, channelId: Long, items: List<Map<String, Any>>) {
        for (item in items) {
            try {
                val apiName = item["apiName"] as? String ?: "CloudStream"
                val url = item["url"] as? String ?: ""
                val name = item["name"] as? String ?: "Unknown"
                val posterUrl = item["posterUrl"] as? String
                val description = item["description"] as? String

                val nameBase64 = Base64.encodeToString(apiName.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                val urlBase64 = Base64.encodeToString(url.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                
                // Construct deep link URI that matches the intent filter if we were to open it
                // using "csshare" scheme as per native app
                val csshareUri = "$APP_STRING_SHARE:$nameBase64?$urlBase64"

                val builder = PreviewProgram.Builder()
                    .setChannelId(channelId)
                    .setTitle(name)
                    .setDescription(description ?: apiName)
                    .setContentId(url)
                    .setType(TvContractCompat.PreviewPrograms.TYPE_MOVIE)
                    .setIntentUri(csshareUri.toUri())
                    .setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_2_3)

                // Validate poster URL before setting
                if (!posterUrl.isNullOrBlank() && posterUrl.startsWith("http")) {
                    builder.setPosterArtUri(posterUrl.toUri())
                }
                val program = builder.build()

                val uri = context.contentResolver.insert(
                    TvContractCompat.PreviewPrograms.CONTENT_URI,
                    program.toContentValues()
                )

                if (uri != null) {
                    val programId = ContentUris.parseId(uri)
                    context.saveProgramId(programId)
                    Log.d("TvChannelUtils", "Inserted program $name, ID=$programId")
                } else {
                    Log.e("TvChannelUtils", "Insert failed for $name")
                }

            } catch (error: Exception) {
                Log.e("TvChannelUtils", "Error inserting ${item}: $error")
            }
        }
    }

    fun deleteStoredPrograms(context: Context) {
        val programIds = context.getStoredProgramIds()

        for (id in programIds) {
            val uri = ContentUris.withAppendedId(TvContractCompat.PreviewPrograms.CONTENT_URI, id)
            try {
                val rowsDeleted = context.contentResolver.delete(uri, null, null)
                if (rowsDeleted > 0 || true) { // Always remove from list if attempted, to avoid stuck IDs
                    context.removeProgramId(id) 
                }
            } catch (e: Exception) {
                Log.e("ProgramDelete", "Failed to delete program ID: $id", e)
                 // If permission denied or other persistent error, maybe keep it? But safe to remove from local list to avoid loop
                 context.removeProgramId(id)
            }
        }
        Log.d("ProgramDelete", "Finished deleting stored programs")
    }

    fun createTvChannel(context: Context) {
        val existingId = getChannelId(context, context.getString(R.string.app_name))
        if (existingId != null) {
             Log.d("TvChannelUtils", "Channel already exists: $existingId")
             return
        }

        val componentName = ComponentName(context, MainActivity::class.java)
        // Ensure you have a resource for this, or fallback
        val iconUri = "android.resource://${context.packageName}/mipmap/ic_launcher".toUri()
        val inputId = TvContractCompat.buildInputId(componentName)
        val channel = Channel.Builder()
            .setType(TvContractCompat.Channels.TYPE_PREVIEW)
            .setAppLinkIconUri(iconUri)
            .setDisplayName(context.getString(R.string.app_name))
            .setAppLinkIntent(Intent(Intent.ACTION_VIEW).apply {
                // Adjust to match your app's deep link if needed
                data = "skystreamapp://open".toUri()
            })
            .setInputId(inputId)
            .build()

        try {
            val channelUri = context.contentResolver.insert(
                TvContractCompat.Channels.CONTENT_URI,
                channel.toContentValues()
            )

            channelUri?.let {
                val channelId = ContentUris.parseId(it)
                TvContractCompat.requestChannelBrowsable(context, channelId)
                Log.d("TvChannelUtils", "Channel Created: $channelId")
            }
        } catch (e: Exception) {
            Log.e("TvChannelUtils", "Failed to create channel: $e")
        }
    }
}
