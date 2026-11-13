package uk.co.meditationtimer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp

@Composable
fun QuoteCard(quote: Quote?, openAttribution: () -> Unit) {
    if (quote == null) return
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(quote.text, style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic)
            Text("— ${quote.author}", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = openAttribution) { Text("Inspirational quotes provided by ZenQuotes API") }
        }
    }
}
