package com.janreins.piso.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Reusable rounded card with clean padding and subtle border.
 */
@Composable
fun PisoCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    cornerRadius: Dp = 22.dp,
    elevation: Dp = 0.dp,
    contentPadding: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            border = BorderStroke(1.dp, borderColor),
            elevation = CardDefaults.cardElevation(defaultElevation = elevation)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(contentPadding),
                content = content
            )
        }
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            border = BorderStroke(1.dp, borderColor),
            elevation = CardDefaults.cardElevation(defaultElevation = elevation)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(contentPadding),
                content = content
            )
        }
    }
}
