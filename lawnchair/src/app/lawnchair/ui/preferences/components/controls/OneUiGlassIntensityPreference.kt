/*
 * Copyright (C) 2026 Lawnchair
 * Licensed under the Apache License, Version 2.0
 */
package app.lawnchair.ui.preferences.components.controls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import kotlin.math.roundToInt

/** 0-100 One UI glass slider. Changes are applied live in 5-point steps. */
@Composable
fun OneUiGlassIntensityPreference(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
) {
    var sliderValue by remember { mutableFloatStateOf(value.toFloat()) }

    LaunchedEffect(value) {
        sliderValue = value.toFloat()
    }

    PreferenceTemplate(
        title = {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = label, modifier = Modifier.weight(1f).padding(end = 8.dp))
                Text(
                    text = "${sliderValue.roundToInt()}%",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        description = {
            Slider(
                value = sliderValue,
                onValueChange = { raw ->
                    val snapped = ((raw / 5f).roundToInt() * 5).coerceIn(0, 100)
                    if (snapped.toFloat() != sliderValue) {
                        sliderValue = snapped.toFloat()
                        onValueChange(snapped)
                    }
                },
                valueRange = 0f..100f,
                steps = 19,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp).height(24.dp),
            )
        },
    )
}
