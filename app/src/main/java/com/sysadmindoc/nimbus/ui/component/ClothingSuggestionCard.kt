package com.sysadmindoc.nimbus.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Umbrella
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sysadmindoc.nimbus.ui.theme.NimbusBlueAccent
import com.sysadmindoc.nimbus.ui.theme.NimbusRainBlue
import com.sysadmindoc.nimbus.ui.theme.NimbusTextSecondary
import com.sysadmindoc.nimbus.ui.theme.NimbusWarning
import com.sysadmindoc.nimbus.util.ClothingCategory
import com.sysadmindoc.nimbus.util.ClothingSuggestion

@Composable
fun ClothingSuggestionCard(
    suggestions: List<ClothingSuggestion>,
    modifier: Modifier = Modifier,
) {
    if (suggestions.isEmpty()) return

    WeatherCard(title = "What to Wear", modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth()) {
            suggestions.forEach { suggestion ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 3.dp),
                ) {
                    val icon = when (suggestion.category) {
                        ClothingCategory.RAIN -> Icons.Outlined.Umbrella
                        ClothingCategory.ACCESSORIES -> if (suggestion.text.contains("Sun", ignoreCase = true))
                            Icons.Outlined.WbSunny else Icons.Outlined.Checkroom
                        else -> Icons.Outlined.Checkroom
                    }
                    val tint = when (suggestion.category) {
                        ClothingCategory.RAIN -> NimbusRainBlue
                        ClothingCategory.ACCESSORIES -> NimbusWarning
                        else -> NimbusBlueAccent
                    }
                    Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = tint)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = suggestion.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = NimbusTextSecondary,
                    )
                }
            }
        }
    }
}
