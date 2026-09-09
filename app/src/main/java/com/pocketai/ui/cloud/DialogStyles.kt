package com.pocketai.ui.cloud

import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.pocketai.ui.theme.CyanAccent
import com.pocketai.ui.theme.DarkBorder
import com.pocketai.ui.theme.SlateDark800
import com.pocketai.ui.theme.TextMutedDark
import com.pocketai.ui.theme.TextPrimaryDark
import com.pocketai.ui.theme.TextSecondaryDark

@Composable
fun dialogTextFieldColors(): TextFieldColors {
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = TextPrimaryDark,
        unfocusedTextColor = TextPrimaryDark,
        focusedContainerColor = SlateDark800,
        unfocusedContainerColor = SlateDark800,
        focusedBorderColor = CyanAccent,
        unfocusedBorderColor = DarkBorder,
        focusedLabelColor = CyanAccent,
        unfocusedLabelColor = TextSecondaryDark,
        cursorColor = CyanAccent,
        focusedPlaceholderColor = TextMutedDark,
        unfocusedPlaceholderColor = TextMutedDark
    )
}
