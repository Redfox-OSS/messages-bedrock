package dev.redfox.messages.feature.onboarding

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import dev.redfox.bedrock.components.AuraCard
import dev.redfox.bedrock.components.BrButton
import dev.redfox.bedrock.components.BrCheckbox
import dev.redfox.bedrock.components.IconTile
import dev.redfox.bedrock.components.ListGroup
import dev.redfox.bedrock.components.ListRow
import dev.redfox.bedrock.components.SectionHeader
import dev.redfox.bedrock.effects.rememberAura
import dev.redfox.bedrock.motion.BrTransitions
import dev.redfox.bedrock.motion.RevealColumn
import dev.redfox.bedrock.motion.revealOnEnter
import dev.redfox.bedrock.theme.BedrockTheme
import dev.redfox.bedrock.theme.BrSpacing
import dev.redfox.messages.core.model.DeviceSetupStatus
import dev.redfox.messages.core.ui.R
import dev.redfox.messages.core.ui.mvi.CollectEffects
import kotlinx.serialization.Serializable

@Serializable
data object OnboardingKey : NavKey

private val RuntimePermissions = arrayOf(
    Manifest.permission.READ_CONTACTS,
    Manifest.permission.READ_PHONE_STATE,
    Manifest.permission.POST_NOTIFICATIONS,
)

@Composable
fun OnboardingRoute(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val refresh = { viewModel.dispatch(OnboardingIntent.Refresh) }

    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh() }
    val permissionsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh() }
    val batteryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh() }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh() }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            OnboardingEffect.RequestDefaultSmsRole -> {
                val roles = context.getSystemService(RoleManager::class.java)
                roleLauncher.launch(roles.createRequestRoleIntent(RoleManager.ROLE_SMS))
            }
            OnboardingEffect.RequestRuntimePermissions -> permissionsLauncher.launch(RuntimePermissions)
            OnboardingEffect.RequestBatteryExemption -> batteryLauncher.launch(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${context.packageName}".toUri())
            )
            OnboardingEffect.Finished -> onFinished()
        }
    }

    OnboardingScreen(state = state, onIntent = viewModel::dispatch)
}

@Composable
fun OnboardingScreen(
    state: OnboardingState,
    onIntent: (OnboardingIntent) -> Unit,
) {
    val colors = BedrockTheme.colors
    val type = BedrockTheme.typography
    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = BrSpacing.Gutter)
                .padding(top = BrSpacing.XXL, bottom = 160.dp),
        ) {
            WelcomeCard(Modifier.revealOnEnter(0))
            Spacer(Modifier.height(BrSpacing.Section))
            RevealColumn(modifier = Modifier.fillMaxWidth()) {
                SectionHeader("Setup")
                SetupGroup(status = state.status, onIntent = onIntent)
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(colors.background)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = BrSpacing.Gutter, vertical = BrSpacing.L),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BrButton(
                text = "Continue",
                onClick = { onIntent(OnboardingIntent.ContinueClicked) },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.canContinue,
            )
            AnimatedVisibility(
                visible = !state.status.isDefaultSmsApp,
                enter = BrTransitions.expandIn,
                exit = BrTransitions.collapseOut,
            ) {
                Text(
                    "Set Messages as your default SMS app to continue",
                    style = type.caption,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(top = BrSpacing.M)
                )
            }
        }
    }
}

/** The one hero on the screen: the app's name and promise, on the accent's aura. */
@Composable
private fun WelcomeCard(modifier: Modifier = Modifier) {
    val colors = BedrockTheme.colors
    val type = BedrockTheme.typography
    AuraCard(aura = rememberAura(colors.accent), modifier = modifier.fillMaxWidth()) {
        IconTile(
            painterResource(R.drawable.ic_chat),
            tint = colors.textPrimary,
            container = colors.glass,
            size = 48.dp,
            iconSize = 24.dp,
        )
        Spacer(Modifier.height(BrSpacing.XL))
        Text("Messages", style = type.title, color = colors.textPrimary)
        Spacer(Modifier.height(BrSpacing.XS))
        Text(
            "Fast, private SMS & MMS. Three quick steps and you're in.",
            style = type.body,
            color = colors.textPrimary.copy(alpha = 0.8f),
        )
    }
}

@Composable
private fun SetupGroup(status: DeviceSetupStatus, onIntent: (OnboardingIntent) -> Unit) {
    ListGroup {
        SetupRow(
            icon = R.drawable.ic_sms,
            title = "Default SMS app",
            pending = "Required to send and receive texts",
            done = status.isDefaultSmsApp,
            divider = true,
            onClick = { onIntent(OnboardingIntent.DefaultSmsClicked) },
        )
        SetupRow(
            icon = R.drawable.ic_contacts,
            title = "Contacts & notifications",
            pending = "Names, and alerts for new texts",
            done = status.hasRuntimePermissions,
            divider = true,
            onClick = { onIntent(OnboardingIntent.PermissionsClicked) },
        )
        SetupRow(
            icon = R.drawable.ic_battery_full,
            title = "Run in background",
            pending = "So texts are never delayed",
            done = status.isIgnoringBatteryOptimizations,
            divider = false,
            onClick = { onIntent(OnboardingIntent.BatteryClicked) },
        )
    }
}

@Composable
private fun SetupRow(
    @DrawableRes icon: Int,
    title: String,
    pending: String,
    done: Boolean,
    divider: Boolean,
    onClick: () -> Unit,
) {
    val colors = BedrockTheme.colors
    ListRow(
        title = title,
        subtitle = if (done) "Done" else pending,
        leading = {
            IconTile(
                painterResource(icon),
                tint = if (done) colors.accentSoft else colors.textSecondary,
                container = if (done) colors.accentContainer else colors.surfaceHigh,
            )
        },
        // The row and the box do the same thing: open the step that grants it.
        trailing = { BrCheckbox(checked = done, onCheckedChange = { onClick() }) },
        divider = divider,
        onClick = onClick,
    )
}
