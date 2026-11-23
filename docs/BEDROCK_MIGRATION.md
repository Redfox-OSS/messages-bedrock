# Obsidian → Bedrock migration guide (Messages on Bedrock)

This repository is Messages rebuilt on the **Bedrock** design system instead of Obsidian. Bedrock's
source is at `/Users/debz/debz/Bedrock` (`bedrock/src/main/java/dev/redfox/bedrock/`): read its
`docs/GUIDE.md` and the component sources before converting a screen. Obsidian's source (for what a
call used to do) is at `/Users/debz/debz/obsidian/obsidian/src/main/java/dev/redfox/obsidian/` (Obsidian 2.0.0, which Messages used).

**Goal:** every screen looks and moves like Bedrock, not like Obsidian with renamed imports. Keep
behaviour, state, intents, navigation and accessibility exactly as they are; change only the UI.

## The Bedrock rules (non-negotiable)

1. **No glows.** Delete every `Modifier.glow(...)`, `breathingGlow`, `ambientFloat`. Do not replace
   them with shadows. Colour lives *inside* surfaces: accent containers, or an `AuraCard` /
   `Modifier.aura(...)` for at most one hero surface per screen. The only shadow is `floatShadow`,
   for floating things (FAB, toasts); Bedrock components already apply it.
2. **Nothing overshoots.** No spring with damping < 1. Use `BrMotion.quick()` / `settle()` /
   `glide()`; timed motion uses `BrMotion.Calm` and `BrDurations`. Delete `popOnSelect`, `popIn`
   (use `revealOnEnter` or nothing), icon spins/twists, bounces.
3. **Sentence case everywhere.** "REPEAT" → "Repeat", "NEXT ALARM" → "Next alarm". No
   `.uppercase()`, no tracked-out labels. Hierarchy comes from size, weight and the text tones.
4. **Tabular numbers**: times, counts and durations use `figure`, `figureLarge` or `numeral`.
5. **Concentric corners**: an inset rounded thing inside a rounded parent uses
   `BrShapes.concentric(outerRadius, padding)`.
6. **Tokens only** (`BedrockTheme.colors`, `BedrockTheme.typography`, `BrShapes`, `BrSpacing`,
   `BrMotion`, `BrDurations`). No `ObsidianTheme`, no `MaterialTheme.*`, no hard-coded colours
   (except brand colours for link buttons in `BrandLinkButtons.kt` / `LinkBrands.kt` and
   photo scrims where they already exist).
7. **Taps** use `Modifier.pressable { }` (same name in Bedrock), never ripples. Press scales: rows
   0.99, cards 0.98, buttons 0.97, chips/icon buttons 0.94.
8. **Write like a person**: empty states say what will appear and how; keep existing copy unless
   it's uppercase or robotic.

## Imports

| Obsidian | Bedrock |
|---|---|
| `dev.redfox.obsidian.theme.*` | `dev.redfox.bedrock.theme.*` |
| `dev.redfox.obsidian.effects.*` | `dev.redfox.bedrock.effects.*` |
| `dev.redfox.obsidian.motion.*` | `dev.redfox.bedrock.motion.*` |
| `dev.redfox.obsidian.components.*` | `dev.redfox.bedrock.components.*` |
| `dev.redfox.obsidian.navigation.*` | `dev.redfox.bedrock.navigation.*` |

After converting, a file must contain **no** `dev.redfox.obsidian` import.

## Tokens

**Colours** (`ObsidianTheme.colors.X` → `BedrockTheme.colors.Y`)

| Obsidian | Bedrock |
|---|---|
| `background` | `background` |
| `card` | `surface` |
| `cardInner`, `tile` | `surfaceRaised` |
| `tileHigh`, `switchTrackOff` | `surfaceHigh` |
| `cardGlass`, `headerScrim`, `navScrim` | `glass` (use `Modifier.glass(shape)`) |
| `border` / `borderStrong` | `line` / `lineStrong` |
| `divider` | `divider` |
| `textPrimary/Secondary/Tertiary/Disabled` | same names |
| `accent` | `accentMain` |
| `accentLight` | `accentSoft` |
| `onAccent` / `onAccentLight` | `onAccent` |
| `accentContainer` / `accentContainerStrong` | same names |
| `accentBorder` | `accentMain.copy(alpha = 0.4f)` (rarely needed: selected Bedrock controls have no border) |
| `accentGlow` | delete the glow |
| `warning` / `error` | `caution` / `critical` |

Also available: `ink` / `onInk` (the warm-white high-contrast pill), `positive`, `glassStrong`.

**Type** (`ObsidianTheme.typography.X` → `BedrockTheme.typography.Y`)

| Obsidian | Bedrock |
|---|---|
| `numericLarge` | `numeral` (56sp Light) |
| `numericTile` | `figureLarge` (32sp) or a `numeral` copy with a smaller `fontSize` for big picker digits |
| `numericMedium` | `figureLarge` |
| `numericSmall` | `figure` |
| `unit` (AM/PM, %) | `caption` or `labelSmall` |
| `screenTitle` | `title` |
| `title` | `headline` |
| `headerTitle` | `label` |
| `bodyLarge` | `bodyLarge` |
| `body` | `body` |
| `label` | `labelSmall` |
| `labelStrong` | `label` |
| `sectionLabel`, `caption` (uppercase eyebrows) | `caption` / `label` in `textSecondary`, sentence case |
| `button` | `label` |
| `InterFontFamily` | `BedrockFontFamily` |

**Shapes / spacing**: `ObShapes.Card` → `BrShapes.Large` (22), `CardLarge` → `XLarge` (30),
`Tile` → `Medium` (16), `Small` → `Small`, `Checkbox` → `XSmall`, `Pill` → `Pill`.
`ScreenGutter` / `ObSpacing.Gutter` → `BrSpacing.Gutter`. Other `ObSpacing` values → `BrSpacing`
(XXS 2, XS 4, S 8, M 12, L 16, XL 24, XXL 32, Section 40) or plain dp.

## Effects and motion

| Obsidian | Bedrock |
|---|---|
| `glow(...)`, `breathingGlow`, `ambientFloat` | delete |
| `glassSource()` | `glassSource()` |
| `glassEffect(tint)` | `glass(shape, tint)` (clips to the shape and adds the lit rim) |
| `pressable(...)` | `pressable(...)` |
| `LocalHazeState` | `dev.redfox.bedrock.effects.LocalHazeState` |
| `shimmer()` / `Placeholder` | `breathe()` / `Placeholder` |
| `ObMotion.snappy()` | `BrMotion.quick()` |
| `ObMotion.gentle()`, `ObMotion.bouncy()` | `BrMotion.settle()` (or `glide()` for big surfaces) |
| `ObMotion.EaseOutEmphasized` | `BrMotion.Calm` |
| `ObDurations.Exit/Short/Enter/Long` | `BrDurations.Fade/Swap/Enter/Linger` |
| `RevealScope`, `revealOnEnter(i)`, `RevealColumn` | same names (Bedrock: rise 14dp + fade, no scale) |
| `popIn()` | `revealOnEnter(0)` or remove |
| `popOnSelect(...)` | delete |
| `RollingText(text, style, color)` | `RollingNumber(text, style, color)` for digits; `SwapText` for words |
| `RollSwap` (transitionSpec) | `BrTransitions.swap`, or replace the AnimatedContent with `SwapText` |
| `HorizontalReveal(visible) { }` | `AnimatedVisibility(visible, enter = BrTransitions.revealStart, exit = BrTransitions.concealStart) { }` |
| `ExpandEnter` / `CollapseExit` | `BrTransitions.expandIn` / `BrTransitions.collapseOut` |
| `HeaderActionsSwap` | `BrTransitions.swap` |
| hand-written `spring(dampingRatio = 0.x …)` / bouncy `tween`s | `BrMotion.*` springs or `tween(BrDurations.X, easing = BrMotion.Calm)` |

## Components

| Obsidian | Bedrock |
|---|---|
| `PrimaryButton(text, onClick, icon = R.drawable.x)` | `BrButton(text, onClick, Modifier.fillMaxWidth(), icon = painterResource(R.drawable.x))` (Primary, Large) |
| `ActionPillButton(text, onClick, icon, primary)` | `BrButton(text, onClick, icon = icon, variant = if (primary) ButtonVariant.Primary else ButtonVariant.Tonal)` |
| `PillButton(text, onClick, icon)` | `BrButton(text, onClick, variant = ButtonVariant.Tonal, size = ButtonSize.Small, icon = …)` |
| `HeaderIconButton(R.drawable.x \| Icons.Rounded.X, desc, onClick, tint, enabled)` | `BrIconButton(painterResource(R.drawable.x) \| rememberVectorPainter(Icons.Rounded.X), desc, onClick, variant = IconButtonVariant.Glass, tint = …, enabled = …)` |
| `ObFab(icon, desc, onClick)` | `BrFab(painterResource(icon), desc, onClick)` |
| `ObSwitch(checked, onCheckedChange, size = …)` | `BrSwitch(checked, onCheckedChange)` (one size) |
| `ObCheckbox(checked, onCheckedChange)` | `BrCheckbox(checked, onCheckedChange)` (32dp; make the row toggle it too) |
| `ObRadioButton` | `BrRadio` |
| `ChoicePill(text, selected, onClick)` | `BrChip(label = text, selected, onClick)` (selected chip turns warm white) |
| `DayChip(letter, selected, onClick)` | `BrDayChip(label = letter, selected, onClick, contentDescription = "Monday" …)` |
| `SegmentedPill(options: List<SegmentOption>, …)` | `SegmentedControl(options = labels, selectedIndex, onSelect)` |
| `ObCard(…)` | `BrCard(…)` (pass `Modifier.fillMaxWidth()` when it should span) |
| `IconTile(R.drawable.x, size, background, tint)` | `IconTile(painterResource(R.drawable.x), tint = …, container = …, size = …)` (default 34dp) |
| `SectionLabel(text, trailing)` | `SectionHeader(title = sentence-case text, actionLabel, onAction)`; for a custom trailing, a `Row` with `Text(style = label, color = textSecondary)` |
| `ObListRow(title, icon, subtitle, onClick, trailing)` | `ListRow(title, subtitle = …, leading = { IconTile(…) }, trailing = { … }, chevron, divider, onClick)` inside a `ListGroup { }` for grouped rows (set `divider = true` on all but the last) |
| `ObDivider` | `BrDivider(inset = …)` |
| `EmptyState(icon, title, subtitle)` | `EmptyState(painterResource(icon), title, message, actionLabel?, onAction)` |
| `ObDropdownMenu(expanded, onDismissRequest) { ObMenuItem(text, onClick) }` | `BrMenu(expanded, onDismissRequest) { BrMenuItem(text, onClick, icon?, selected, destructive) }` |
| `if (show) ObAlertDialog(title, text, confirmLabel, onConfirm, onDismiss, destructive)` | `BrDialog(visible = show, onDismiss, title, message = text, confirmLabel, onConfirm, destructive = …)` **always composed**, driven by `visible`, so the exit animation plays. Custom content: `BrDialogFrame(visible, onDismiss) { … }` |
| `if (show) ObSheet(onDismiss, title) { }` | `BrSheet(visible = show, onDismiss, title = …) { }` always composed |
| `ObTextField(…)` | `BrTextField(value, onValueChange, placeholder, label, supportingText, isError, singleLine, leadingIcon = painter)` |
| `ObSearchField(query, onQueryChange, placeholder)` | `BrSearchField(query, onQueryChange, placeholder)` |
| `ObSlider(value, onValueChange, valueRange, steps)` | `BrSlider(value, onValueChange, valueRange = …, steps = …)` (`style = SliderStyle.Thick` for levels like volume) |
| `ObProgressBar` / `ObSpinner` | `BrProgressBar` / `BrSpinner` |
| `CountBadge` | `BrBadge` |
| `ObAvatar` | `BrAvatar` |
| `floatingNavClearance()` | `brNavClearance()` |
| `LargeHeader` / `DetailHeader` (+ `…HeaderHeight`) | the page pattern below |

`BrDialog`, `BrDialogFrame` and `BrSheet` open in their own window, so they cover the nav bar;
place them anywhere in the screen.

## The page pattern (replaces LargeHeader / DetailHeader)

A large title scrolls with the content; a frosted top bar fades in under the status bar as you
scroll, with a small centred title. Actions and back live in the bar.

```kotlin
val listState = rememberLazyListState()
val collapse = rememberTopBarCollapse(listState)        // or rememberTopBarCollapse(scrollState)
Box(Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().glassSource(),
        contentPadding = PaddingValues(top = topBarClearance(), bottom = brNavClearance()), // detail screens: bottom = navigation bar inset + 32.dp
    ) {
        item(key = "title") { BrLargeTitle(title = "Alarms", collapse = collapse, subtitle = optional) }
        // …content, each section padded by BrSpacing.Gutter horizontally…
    }
    BrTopBar(
        title = "Alarms",
        collapse = collapse,
        modifier = Modifier.align(Alignment.TopCenter),
        onBack = null /* or { onBack() } on detail screens */,
    ) { /* actions: BrIconButton(…, variant = IconButtonVariant.Glass) */ }
}
```

For a `Column(Modifier.verticalScroll(scrollState))`, use `rememberTopBarCollapse(scrollState)` and
put `BrLargeTitle` as the column's first child. Screens that don't scroll (timer, stopwatch) can
use a fixed `remember { mutableFloatStateOf(0f) }` collapse. Titles that change (selection mode:
"3 selected") go to both `BrLargeTitle` and `BrTopBar`; `BrLargeTitle` swaps them calmly.

Sections inside a page: `SectionHeader("Repeat")` then the content, with `BrSpacing.Section` (40dp)
between sections and `BrSpacing.Gutter` (20dp) at the page edges.

## Design upgrades that are welcome

- One hero `AuraCard(Aura.of(accent))` per screen where there's a natural hero (onboarding's
  welcome, for example). The inbox and threads have no hero: keep them quiet. Read the accent with `BedrockTheme.colors.accent` (a `BedrockAccent`).
- Lists of settings or rows → `ListGroup { ListRow … }` with hairline dividers.
- Prefer `BrButton` variants over custom button drawing; `Ink` is the warm-white high-contrast
  pill for actions over colour.

## Don't

- Don't change ViewModels, intents, UI state, navigation routes, or anything outside the files
  you were given (except trivial imports).
- Don't run Gradle: another conversion is happening in parallel in the same tree. The lead will
  build and fix compile errors. Be careful with imports and named arguments instead.

## Messages specifics

**Pages.** Obsidian's `TabPage(title, actions) { padding -> }` and `DetailPage(title, onBack, actions) { padding -> }`
become the page pattern above (`BrTopBar` + `BrLargeTitle` + `rememberTopBarCollapse`, or `BrPage` when the
whole screen is one `LazyColumn`). Tab screens pad the bottom by `brNavClearance()`; pushed screens by
the navigation-bar inset + `BrSpacing.XXL`. The thread screen is the exception: it has no large title
(a chat starts at the bottom); give it a `BrTopBar` whose collapse is a fixed `1f` (always frosted,
title showing) with the contact name, and keep its reversed list and composer as they are.

**Components Bedrock doesn't have.** Write these as small private (or `core/ui` internal) composables
using Bedrock tokens, in the file listed:

| Obsidian | Where | Bedrock version |
|---|---|---|
| `ObAvatar` / `ObAvatarPair` | `core/ui/.../components/Avatar.kt` | `BrAvatar` for one; the pair is two smaller `BrAvatar`s offset diagonally, the front one ringed in `background` |
| `CountBadge` | `ConversationRow.kt` | `BrBadge` |
| `CopyPill` | `MessageBubble.kt` | a small `BrChip`/tonal pill with the OTP in `figure`, `pressable`, that swaps its label to "Copied" with `SwapText` |
| `SwipeActionRow` / `SwipeVisual` | `ConversationsScreen.kt` | keep the gesture logic (read Obsidian's `SwipeActionRow.kt`); the revealed action sits on a `surfaceRaised` track with an accent-container (or `critical` for delete) circle behind the icon, settles back with `BrMotion.settle()`, no overshoot |
| `DateDivider` | `ThreadScreen.kt` | centred `caption` text in `textTertiary`, sentence case, no lines |
| `AccentSwatchRow` | `SettingsScreen.kt` | copy `AccentColorPicker` / `AccentSwatch` from `/Users/debz/debz/Alarmzy-Bedrock/app/src/main/java/dev/redfox/alarmzy/presentation/settings/SettingsScreen.kt`, mapping over `AppAccent.entries` with `toBedrock()` from `core/ui/theme/MessagesTheme.kt` |
| `copyToClipboard` | `ThreadScreen.kt` | `LocalClipboard` / `ClipboardManager` directly |
| `SegmentedPill` | | `SegmentedControl(options: List<String>, selectedIndex, onSelect)` |

`AppAccent` is now `Tide, Iris, Moss, Rose, Ember, Sand` (default `Tide`), mapped with `AppAccent.toBedrock()`;
there is no `toAppAccent()` any more.

**Message bubbles.** Outgoing bubbles use the accent container family (`accentContainerStrong` fill,
`textPrimary` text) rather than a saturated accent fill; incoming use `surfaceRaised`. No glows on
bubbles or the composer's send button. Grouped bubbles keep their tight corners on the joined side.

**The inbox.** `ConversationsRoute` gets a new parameter `onNewMessage: () -> Unit` (the app shell
already passes it): a `BrIconButton` with `R.drawable.ic_edit_square`, "New message", in the top bar's
actions next to settings. Selection mode keeps its count title and actions in the top bar.
