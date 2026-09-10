package com.lagradost.cloudstream3.desktop.domain.player.model

import com.lagradost.cloudstream3.desktop.core.preference.DesktopPreferenceStore
import com.lagradost.cloudstream3.desktop.core.preference.Preference
import com.lagradost.cloudstream3.desktop.core.preference.PreferenceStore
import com.lagradost.cloudstream3.desktop.player.PlayerConfig

class PlayerPreferences(
    private val store: PreferenceStore = DesktopPreferenceStore(),
) {
    val hwdec: Preference<String> = store.getString(PlayerConfig.PREF_HWDEC, "auto-safe")
    val subtitleSize: Preference<String> = store.getString(PlayerConfig.PREF_SUB_SIZE, "45")
    val subtitleColor: Preference<String> = store.getString(PlayerConfig.PREF_SUB_COLOR, "#FFFFFF")
    val subtitleBackground: Preference<String> = store.getString(PlayerConfig.PREF_SUB_BG, "#00000000")
    val autoPlay: Preference<Boolean> = store.getBoolean(PlayerConfig.PREF_AUTO_PLAY, true)
    val deband: Preference<Boolean> = store.getBoolean(PlayerConfig.PREF_DEBAND, false)
    val interpolation: Preference<Boolean> = store.getBoolean(PlayerConfig.PREF_INTERPOLATION, false)
    val activeShader: Preference<String> = store.getString(PlayerConfig.PREF_ACTIVE_SHADER, "None")
    val autoSkipIntro: Preference<Boolean> = store.getBoolean(PlayerConfig.PREF_AUTO_SKIP_INTRO, false)
    val autoSkipOutro: Preference<Boolean> = store.getBoolean(PlayerConfig.PREF_AUTO_SKIP_OUTRO, false)
}
