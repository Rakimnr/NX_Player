package com.nextgen.nxplayer.ui.screens.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nextgen.nxplayer.data.local.PreferencesManager
import java.util.Locale
import kotlin.math.roundToInt

private data class Choice<T>(
    val label: String,
    val value: T
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: (() -> Unit)? = null,
    onPrivacyClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager(context.applicationContext) }

    var resumePlayback by remember { mutableStateOf(prefs.resumePlayback) }
    var defaultSpeed by remember { mutableFloatStateOf(prefs.defaultSpeed) }
    var aspectMode by remember { mutableStateOf(prefs.aspectMode) }
    var pipOnBack by remember { mutableStateOf(prefs.pipOnBack) }

    var subtitleEnabledByDefault by remember { mutableStateOf(prefs.subtitleEnabledByDefault) }
    var subtitleFontSize by remember { mutableFloatStateOf(prefs.subtitleFontSize) }
    var subtitleBackgroundAlpha by remember { mutableFloatStateOf(prefs.subtitleBackgroundAlpha) }
    var subtitleTextColor by remember { mutableStateOf(prefs.subtitleTextColor) }
    var subtitleEncoding by remember { mutableStateOf(prefs.subtitleEncoding) }

    var audioBoost by remember { mutableStateOf(prefs.audioBoost) }
    var rememberAudioTrackPerVideo by remember { mutableStateOf(prefs.rememberAudioTrackPerVideo) }
    var preferredAudioLanguage by remember { mutableStateOf(prefs.preferredAudioLanguage) }

    var gesturesEnabled by remember { mutableStateOf(prefs.gesturesEnabled) }
    var doubleTapSeekSeconds by remember { mutableIntStateOf(prefs.doubleTapSeekSeconds) }
    var swipeVolumeEnabled by remember { mutableStateOf(prefs.swipeVolumeEnabled) }
    var swipeBrightnessEnabled by remember { mutableStateOf(prefs.swipeBrightnessEnabled) }

    var longPressKidsLock by remember { mutableStateOf(prefs.longPressKidsLock) }
    var kidsLockDisableVolume by remember { mutableStateOf(prefs.kidsLockDisableVolume) }
    var kidsLockHideControls by remember { mutableStateOf(prefs.kidsLockHideControls) }

    var showResetDialog by remember { mutableStateOf(false) }

    fun reloadFromPrefs() {
        resumePlayback = prefs.resumePlayback
        defaultSpeed = prefs.defaultSpeed
        aspectMode = prefs.aspectMode
        pipOnBack = prefs.pipOnBack

        subtitleEnabledByDefault = prefs.subtitleEnabledByDefault
        subtitleFontSize = prefs.subtitleFontSize
        subtitleBackgroundAlpha = prefs.subtitleBackgroundAlpha
        subtitleTextColor = prefs.subtitleTextColor
        subtitleEncoding = prefs.subtitleEncoding

        audioBoost = prefs.audioBoost
        rememberAudioTrackPerVideo = prefs.rememberAudioTrackPerVideo
        preferredAudioLanguage = prefs.preferredAudioLanguage

        gesturesEnabled = prefs.gesturesEnabled
        doubleTapSeekSeconds = prefs.doubleTapSeekSeconds
        swipeVolumeEnabled = prefs.swipeVolumeEnabled
        swipeBrightnessEnabled = prefs.swipeBrightnessEnabled

        longPressKidsLock = prefs.longPressKidsLock
        kidsLockDisableVolume = prefs.kidsLockDisableVolume
        kidsLockHideControls = prefs.kidsLockHideControls
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsSection(title = "Video") {
                SettingsSwitchRow(
                    title = "Resume playback",
                    description = "Ask to continue from the last saved position.",
                    checked = resumePlayback,
                    onCheckedChange = {
                        resumePlayback = it
                        prefs.resumePlayback = it
                    }
                )

                SettingsChoiceRow(
                    title = "Default speed",
                    description = "Applied when a video starts.",
                    valueLabel = "${formatSpeed(defaultSpeed)}x",
                    choices = listOf(
                        Choice("0.5x", 0.5f),
                        Choice("0.75x", 0.75f),
                        Choice("1.0x", 1.0f),
                        Choice("1.25x", 1.25f),
                        Choice("1.5x", 1.5f),
                        Choice("2.0x", 2.0f)
                    ),
                    onSelected = {
                        defaultSpeed = it
                        prefs.defaultSpeed = it
                    }
                )

                SettingsChoiceRow(
                    title = "Aspect ratio",
                    description = "Default player resize mode.",
                    valueLabel = aspectModeLabel(aspectMode),
                    choices = listOf(
                        Choice("Fit", "FIT"),
                        Choice("Crop", "CROP"),
                        Choice("Stretch", "STRETCH")
                    ),
                    onSelected = {
                        aspectMode = it
                        prefs.aspectMode = it
                    }
                )

                SettingsSwitchRow(
                    title = "Picture-in-picture on back",
                    description = "Prepare for background-style mini playback later.",
                    checked = pipOnBack,
                    onCheckedChange = {
                        pipOnBack = it
                        prefs.pipOnBack = it
                    }
                )
            }

            SettingsSection(title = "Subtitle") {
                SettingsSwitchRow(
                    title = "Enable subtitles by default",
                    description = "Keep subtitle track selection enabled when available.",
                    checked = subtitleEnabledByDefault,
                    onCheckedChange = {
                        subtitleEnabledByDefault = it
                        prefs.subtitleEnabledByDefault = it
                    }
                )

                SettingsSliderRow(
                    title = "Font size",
                    description = "${subtitleFontSize.roundToInt()}sp",
                    value = subtitleFontSize,
                    valueRange = 12f..32f,
                    steps = 9,
                    onValueChange = {
                        subtitleFontSize = it
                        prefs.subtitleFontSize = it
                    }
                )

                SettingsChoiceRow(
                    title = "Text color",
                    description = "Subtitle foreground color preference.",
                    valueLabel = subtitleColorLabel(subtitleTextColor),
                    choices = listOf(
                        Choice("White", "WHITE"),
                        Choice("Yellow", "YELLOW"),
                        Choice("Cyan", "CYAN")
                    ),
                    onSelected = {
                        subtitleTextColor = it
                        prefs.subtitleTextColor = it
                    }
                )

                SettingsSliderRow(
                    title = "Background opacity",
                    description = "${(subtitleBackgroundAlpha * 100f).roundToInt()}%",
                    value = subtitleBackgroundAlpha,
                    valueRange = 0f..1f,
                    steps = 9,
                    onValueChange = {
                        subtitleBackgroundAlpha = it
                        prefs.subtitleBackgroundAlpha = it
                    }
                )

                SettingsChoiceRow(
                    title = "Encoding override",
                    description = "Use Auto unless a subtitle file shows broken characters.",
                    valueLabel = subtitleEncoding,
                    choices = listOf(
                        Choice("Auto", "AUTO"),
                        Choice("UTF-8", "UTF-8"),
                        Choice("UTF-16", "UTF-16"),
                        Choice("ISO-8859-1", "ISO-8859-1"),
                        Choice("Windows-1252", "WINDOWS-1252")
                    ),
                    onSelected = {
                        subtitleEncoding = it
                        prefs.subtitleEncoding = it
                    }
                )
            }

            SettingsSection(title = "Audio") {
                SettingsSwitchRow(
                    title = "Remember audio track per video",
                    description = "Keeps the chosen audio track preference for the same video.",
                    checked = rememberAudioTrackPerVideo,
                    onCheckedChange = {
                        rememberAudioTrackPerVideo = it
                        prefs.rememberAudioTrackPerVideo = it
                    }
                )

                SettingsSwitchRow(
                    title = "Audio boost",
                    description = "Use carefully. Real gain handling should stay inside the player layer.",
                    checked = audioBoost,
                    onCheckedChange = {
                        audioBoost = it
                        prefs.audioBoost = it
                    }
                )

                SettingsChoiceRow(
                    title = "Preferred audio language",
                    description = "Auto keeps the file/player default.",
                    valueLabel = audioLanguageLabel(preferredAudioLanguage),
                    choices = listOf(
                        Choice("Auto", "AUTO"),
                        Choice("English", "en"),
                        Choice("Sinhala", "si"),
                        Choice("Tamil", "ta"),
                        Choice("Hindi", "hi"),
                        Choice("Japanese", "ja"),
                        Choice("Korean", "ko")
                    ),
                    onSelected = {
                        preferredAudioLanguage = it
                        prefs.preferredAudioLanguage = it
                    }
                )

                SettingsActionRow(
                    title = "System equalizer",
                    description = "Opens the device equalizer if the phone provides one.",
                    buttonText = "Open",
                    onClick = {
                        openIntentSafely(
                            context = context,
                            intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL),
                            errorMessage = "System equalizer is not available on this device."
                        )
                    }
                )
            }

            SettingsSection(title = "Gestures") {
                SettingsSwitchRow(
                    title = "Enable player gestures",
                    description = "Master switch for gesture controls.",
                    checked = gesturesEnabled,
                    onCheckedChange = {
                        gesturesEnabled = it
                        prefs.gesturesEnabled = it
                    }
                )

                SettingsChoiceRow(
                    title = "Double tap seek",
                    description = "Seek amount for left/right double tap.",
                    valueLabel = "${doubleTapSeekSeconds}s",
                    choices = listOf(
                        Choice("5 seconds", 5),
                        Choice("10 seconds", 10),
                        Choice("15 seconds", 15),
                        Choice("30 seconds", 30)
                    ),
                    onSelected = {
                        doubleTapSeekSeconds = it
                        prefs.doubleTapSeekSeconds = it
                    }
                )

                SettingsSwitchRow(
                    title = "Swipe for volume",
                    description = "Vertical swipe on the right side.",
                    checked = swipeVolumeEnabled,
                    onCheckedChange = {
                        swipeVolumeEnabled = it
                        prefs.swipeVolumeEnabled = it
                    }
                )

                SettingsSwitchRow(
                    title = "Swipe for brightness",
                    description = "Vertical swipe on the left side.",
                    checked = swipeBrightnessEnabled,
                    onCheckedChange = {
                        swipeBrightnessEnabled = it
                        prefs.swipeBrightnessEnabled = it
                    }
                )
            }

            SettingsSection(title = "Kids Lock") {
                SettingsSwitchRow(
                    title = "Long press to lock",
                    description = "Allows quick locking from the player screen.",
                    checked = longPressKidsLock,
                    onCheckedChange = {
                        longPressKidsLock = it
                        prefs.longPressKidsLock = it
                    }
                )

                SettingsSwitchRow(
                    title = "Disable volume while locked",
                    description = "Blocks accidental volume changes while Kids Lock is active.",
                    checked = kidsLockDisableVolume,
                    onCheckedChange = {
                        kidsLockDisableVolume = it
                        prefs.kidsLockDisableVolume = it
                    }
                )

                SettingsSwitchRow(
                    title = "Hide controls while locked",
                    description = "Keeps the lock screen clean and simple.",
                    checked = kidsLockHideControls,
                    onCheckedChange = {
                        kidsLockHideControls = it
                        prefs.kidsLockHideControls = it
                    }
                )
            }

            SettingsSection(title = "Privacy & Permissions") {
                SettingsInfoRow(
                    title = "Storage permission",
                    description = "NX Player requests video library access only when needed. No audio permission is required for video playback."
                )

                SettingsActionRow(
                    title = "Privacy dashboard",
                    description = "Review the app privacy screen.",
                    buttonText = "Open",
                    onClick = {
                        if (onPrivacyClick != null) {
                            onPrivacyClick()
                        } else {
                            Toast.makeText(context, "Privacy screen is unavailable here.", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                SettingsActionRow(
                    title = "Android app permissions",
                    description = "Open system settings for NX Player.",
                    buttonText = "Open",
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        openIntentSafely(
                            context = context,
                            intent = intent,
                            errorMessage = "Unable to open app settings."
                        )
                    }
                )
            }

            SettingsSection(title = "About") {
                SettingsInfoRow(
                    title = "NX Player",
                    description = "Version ${getAppVersionName(context)}"
                )

                SettingsInfoRow(
                    title = "Donation",
                    description = "Disabled for now. Use Google Play Billing only after Play Console products are ready. No ads. No feature paywall."
                )

                SettingsActionRow(
                    title = "Rate on Play Store",
                    description = "Works after the app is published.",
                    buttonText = "Open",
                    onClick = {
                        openPlayStore(context)
                    }
                )

                HorizontalDivider()

                SettingsActionRow(
                    title = "Reset settings",
                    description = "Restore default player preferences.",
                    buttonText = "Reset",
                    onClick = { showResetDialog = true }
                )
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset settings?") },
            text = { Text("This will restore the default video, subtitle, audio, gesture, and Kids Lock settings.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        prefs.resetToDefaults()
                        reloadFromPrefs()
                        showResetDialog = false
                        Toast.makeText(context, "Settings reset", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SettingsInfoRow(
    title: String,
    description: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    description: String,
    buttonText: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        OutlinedButton(onClick = onClick) {
            Text(buttonText)
        }
    }
}

@Composable
private fun SettingsSliderRow(
    title: String,
    description: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps
        )
    }
}

@Composable
private fun <T> SettingsChoiceRow(
    title: String,
    description: String,
    valueLabel: String,
    choices: List<Choice<T>>,
    onSelected: (T) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Button(onClick = { showDialog = true }) {
            Text(valueLabel)
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title) },
            text = {
                Column {
                    choices.forEach { choice ->
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                onSelected(choice.value)
                                showDialog = false
                            }
                        ) {
                            Text(choice.label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

private fun openIntentSafely(
    context: Context,
    intent: Intent,
    errorMessage: String
) {
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
    } catch (_: SecurityException) {
        Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
    }
}

private fun openPlayStore(context: Context) {
    val packageName = context.packageName

    val marketIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("market://details?id=$packageName")
    )

    val browserIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
    )

    try {
        context.startActivity(marketIntent)
    } catch (_: ActivityNotFoundException) {
        openIntentSafely(
            context = context,
            intent = browserIntent,
            errorMessage = "Unable to open Play Store."
        )
    }
}

private fun getAppVersionName(context: Context): String {
    return runCatching {
        context.packageManager
            .getPackageInfo(context.packageName, 0)
            .versionName
            ?: "1.0.0"
    }.getOrDefault("1.0.0")
}

private fun formatSpeed(value: Float): String {
    return if (value % 1f == 0f) {
        value.toInt().toString()
    } else {
        String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
    }
}

private fun aspectModeLabel(value: String): String {
    return when (value) {
        "CROP" -> "Crop"
        "STRETCH" -> "Stretch"
        else -> "Fit"
    }
}

private fun subtitleColorLabel(value: String): String {
    return when (value) {
        "YELLOW" -> "Yellow"
        "CYAN" -> "Cyan"
        else -> "White"
    }
}

private fun audioLanguageLabel(value: String): String {
    return when (value) {
        "en" -> "English"
        "si" -> "Sinhala"
        "ta" -> "Tamil"
        "hi" -> "Hindi"
        "ja" -> "Japanese"
        "ko" -> "Korean"
        else -> "Auto"
    }
}