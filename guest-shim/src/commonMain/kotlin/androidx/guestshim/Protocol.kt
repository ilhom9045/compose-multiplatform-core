/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.guestshim

// Copied verbatim from the host that renders this wire:
//   NativeCMPWeb/protocol/src/commonMain/kotlin/com/ilhom/protocol/NodeType.kt
//
// The host owns these numbers — its NodeRenderer switches on them to pick a real Compose
// component, and its NativeRenderTree switches on the mutation ids. They are copied whole rather
// than kept as the subset the guest happens to use, because a hand-maintained subset drifts
// silently: this file previously had Text = 1, which is the host's FontSize, so a string prop
// would have arrived as a font size with nothing failing anywhere.
//
// Nothing here may be renumbered on this side alone.

/**
 * Тип компонента — передаётся как Int через JSI и JNI.
 * C++ слой не знает что означают эти числа — соглашение только между JS и Native (Kotlin).
 */
object NodeType {
    const val Root    = 0
    const val Text    = 1
    const val Column  = 2
    const val Row     = 3
    const val Box     = 4
    const val Button  = 5
    const val Image   = 6
    const val Spacer  = 7
    const val LazyColumn = 8
    const val LazyRow    = 9
    const val TextField  = 10
    const val Checkbox   = 11
    const val Switch     = 12
    const val RadioButton = 13
    const val CircularProgressIndicator = 14
    const val LinearProgressIndicator   = 15
    const val HorizontalDivider = 16
    const val VerticalDivider   = 17
    const val Slider     = 18
}

/**
 * Ключи пропсов — передаются как Int.
 * Должны совпадать с node_types.h на C++ стороне.
 */
object PropKey {
    const val Text       = 0
    const val FontSize   = 1
    const val Color      = 2
    const val FontWeight = 3
    const val MaxLines   = 4
    const val Alpha      = 5
    const val Enabled    = 6
    // Modifier
    const val PaddingTop    = 10
    const val PaddingBottom = 11
    const val PaddingStart  = 12
    const val PaddingEnd    = 13
    const val Width         = 14
    const val Height        = 15
    const val BackgroundColor = 16
    const val Weight        = 18
    const val FillMaxWidth      = 20
    const val FillMaxHeight     = 21
    const val ScrollVertical    = 22
    const val ScrollHorizontal  = 23
    // TextField
    const val Placeholder    = 19
    const val Label          = 24
    const val ReadOnly       = 25
    const val IsError        = 26
    const val SingleLine     = 27
    const val MinLines       = 28
    const val KeyboardType   = 29
    const val ImeAction      = 30
    // Selection (TextField cursor control)
    const val SelectionStart = 52
    const val SelectionEnd   = 53
    // Clip shape group (clip + border + shadow all read these)
    const val ClipShapeType           = 43  // 0=rectangle, 1=rounded, 2=circle
    const val CornerRadiusTopStart    = 44
    const val CornerRadiusTopEnd      = 45
    const val CornerRadiusBottomEnd   = 46
    const val CornerRadiusBottomStart = 47
    // Other modifier props
    const val BorderWidth       = 31
    const val BorderColor       = 32
    const val OffsetX           = 33
    const val OffsetY           = 34
    const val AspectRatio       = 35
    const val Rotate            = 36
    const val Scale             = 37
    const val ZIndex            = 38
    const val ShadowElevation   = 39
    const val ShadowColor       = 40
    const val WrapContentWidth  = 41
    const val WrapContentHeight = 42
    // Row / Column layout props (not modifier props — sent directly via sendInt)
    const val HorizontalArrangement = 60
    const val VerticalArrangement   = 61
    const val HorizontalAlignment   = 62
    const val VerticalAlignment     = 63
    // Scope modifiers (sent via Modifier in RowScope/ColumnScope)
    const val AlignSelf = 64
    // Events
    const val OnClick    = 50
    const val OnChange   = 51
    // Checkbox / Switch
    const val Checked          = 65
    const val OnCheckedChange  = 75
    // RadioButton
    const val Selected         = 66
    // ProgressIndicator  (-1f = indeterminate, ≥ 0f = determinate)
    const val Progress         = 67
    const val StrokeWidth      = 68
    const val TrackColor       = 69
    // Divider
    const val Thickness        = 70
    // Slider
    const val SliderValue      = 71
    const val SliderMin        = 72
    const val SliderMax        = 73
    const val Steps            = 74
    const val OnSliderChange   = 76

    // ── Text props ───────────────────────────────────────────────────────────
    const val TextAlign      = 80
    const val TextOverflow   = 81
    const val LineHeight     = 82
    const val LetterSpacing  = 83
    const val TextDecoration = 84
    const val FontStyle      = 85
    const val SoftWrap       = 86

    // ── Box ──────────────────────────────────────────────────────────────────
    const val ContentAlignment = 87

    // ── Button colors ─────────────────────────────────────────────────────────
    const val ButtonContainerColor         = 90
    const val ButtonContentColor           = 91
    const val ButtonDisabledContainerColor = 92
    const val ButtonDisabledContentColor   = 93
    const val ButtonDefaultElevation       = 94
    const val ButtonPressedElevation       = 95
    const val ButtonFocusedElevation       = 96
    const val ButtonHoveredElevation       = 97
    const val ButtonDisabledElevation      = 98
    const val ContentPaddingStart          = 99
    const val ContentPaddingEnd            = 100
    const val ContentPaddingTop            = 101
    const val ContentPaddingBottom         = 102
    const val ButtonBorderWidth            = 103
    const val ButtonBorderColor            = 104
    const val ButtonShapeType              = 105
    const val ButtonCornerTopStart         = 106
    const val ButtonCornerTopEnd           = 107
    const val ButtonCornerBottomEnd        = 108
    const val ButtonCornerBottomStart      = 109

    // ── Checkbox colors ───────────────────────────────────────────────────────
    const val CheckboxCheckedColor               = 110
    const val CheckboxUncheckedColor             = 111
    const val CheckboxCheckmarkColor             = 112
    const val CheckboxDisabledCheckedColor       = 113
    const val CheckboxDisabledUncheckedColor     = 114
    const val CheckboxDisabledIndeterminateColor = 115

    // ── Switch colors ─────────────────────────────────────────────────────────
    const val SwitchCheckedThumbColor            = 116
    const val SwitchCheckedTrackColor            = 117
    const val SwitchCheckedBorderColor           = 118
    const val SwitchCheckedIconColor             = 119
    const val SwitchUncheckedThumbColor          = 120
    const val SwitchUncheckedTrackColor          = 121
    const val SwitchUncheckedBorderColor         = 122
    const val SwitchUncheckedIconColor           = 123
    const val SwitchDisabledCheckedThumbColor    = 124
    const val SwitchDisabledCheckedTrackColor    = 125
    const val SwitchDisabledCheckedBorderColor   = 126
    const val SwitchDisabledCheckedIconColor     = 127
    const val SwitchDisabledUncheckedThumbColor  = 128
    const val SwitchDisabledUncheckedTrackColor  = 129
    const val SwitchDisabledUncheckedBorderColor = 130
    const val SwitchDisabledUncheckedIconColor   = 131

    // ── RadioButton colors ────────────────────────────────────────────────────
    const val RadioSelectedColor            = 132
    const val RadioUnselectedColor          = 133
    const val RadioDisabledSelectedColor    = 134
    const val RadioDisabledUnselectedColor  = 135

    // ── Slider colors ─────────────────────────────────────────────────────────
    const val SliderThumbColor                  = 136
    const val SliderActiveTrackColor            = 137
    const val SliderActiveTickColor             = 138
    const val SliderInactiveTrackColor          = 139
    const val SliderInactiveTickColor           = 140
    const val SliderDisabledThumbColor          = 141
    const val SliderDisabledActiveTrackColor    = 142
    const val SliderDisabledActiveTickColor     = 143
    const val SliderDisabledInactiveTrackColor  = 144
    const val SliderDisabledInactiveTickColor   = 145
    const val OnValueChangeFinished             = 146

    // ── CircularProgressIndicator ─────────────────────────────────────────────
    const val StrokeCapCircular = 147

    // ── LinearProgressIndicator ───────────────────────────────────────────────
    const val StrokeCapLinear = 148
    const val GapSize         = 149

    // ── TextField colors ──────────────────────────────────────────────────────
    const val TFocusedTextColor          = 150
    const val TUnfocusedTextColor        = 151
    const val TDisabledTextColor         = 152
    const val TErrorTextColor            = 153
    const val TFocusedContainerColor     = 154
    const val TUnfocusedContainerColor   = 155
    const val TDisabledContainerColor    = 156
    const val TErrorContainerColor       = 157
    const val TFocusedIndicatorColor     = 158
    const val TUnfocusedIndicatorColor   = 159
    const val TDisabledIndicatorColor    = 160
    const val TErrorIndicatorColor       = 161
    const val TFocusedLabelColor         = 162
    const val TUnfocusedLabelColor       = 163
    const val TDisabledLabelColor        = 164
    const val TErrorLabelColor           = 165
    const val TFocusedPlaceholderColor   = 166
    const val TUnfocusedPlaceholderColor = 167
    const val TDisabledPlaceholderColor  = 168
    const val TErrorPlaceholderColor     = 169
    const val TextFieldShapeType         = 170
    const val TextFieldCornerTopStart    = 171
    const val TextFieldCornerTopEnd      = 172
    const val TextFieldCornerBottomEnd   = 173
    const val TextFieldCornerBottomStart = 174

    // ── InteractionSource ─────────────────────────────────────────────────────
    const val InteractionSourceId = 175

    // ── CircularProgressIndicator extra ───────────────────────────────────────
    const val TrackColorCircular = 176

    // ── BoxScope child alignment ───────────────────────────────────────────────
    const val BoxAlignSelf = 177

    // ── TextField visual transformation ──────────────────────────────────────
    const val TextTransformation     = 178  // 0=None, 1=Password
    const val TextTransformationMask = 179  // Char.code — символ маски для Password

    // ── Image ─────────────────────────────────────────────────────────────────
    const val ImageUrl              = 180  // String — URL или имя ресурса
    const val ImageContentScale     = 181  // Int: 0=Crop,1=Fit,2=FillBounds,3=FillHeight,4=FillWidth,5=Inside,6=None
    const val ImageContentDesc      = 182  // String — описание для accessibility
    const val ImageAlignment        = 183  // Int: 0-8 BiasAlignment (TopStart…BottomEnd)
}

/**
 * Тип значения пропса — чтобы C++ знал как интерпретировать int bits.
 * C++ слой не знает что означают эти числа — соглашение только между JS и Native (Kotlin).
 */
object PropValueType {
    const val Int      = 0
    const val Float    = 1   // bits передаются через Float.toBits()
    const val String   = 2   // значение — индекс в stringPool
    const val Bool     = 3
    const val Callback = 4   // valueBits игнорируется; native регистрирует event callback
}

/** Mutation kinds, sent as the first Int of every 7-Int mutation record. */
object MutationType {
    const val Create = 0
    const val Insert = 1
    const val Remove = 2
    const val Move = 3
    const val Delete = 4
}

/** Thrown by declarations that exist for source compatibility but have no wire mapping. */
class UnsupportedInGuestException(call: String) :
    UnsupportedOperationException("$call is not supported in the guest runtime")
