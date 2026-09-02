package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.ExpenseCategory
import com.example.data.model.Member
import com.example.data.model.MemberSplit
import com.example.data.model.SplitType
import com.example.ui.components.CategoryIconBadge
import com.example.ui.components.parseColorHex
import com.example.voice.ExpenseVoiceParser
import com.example.voice.ParsedVoiceExpense
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceExpenseDialog(
    members: List<Member>,
    currencySymbol: String = "$",
    memberMonthlyEarnings: Map<Long, Double> = emptyMap(),
    onDismiss: () -> Unit,
    onEditInForm: (ParsedVoiceExpense) -> Unit,
    onQuickSave: (
        title: String,
        amount: Double,
        category: String,
        paidByMemberId: Long,
        date: Long,
        notes: String,
        splitType: SplitType,
        splits: List<MemberSplit>
    ) -> Unit
) {
    var spokenText by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }

    val memberNames = remember(members) { members.map { it.name } }
    val parsedResult = remember(spokenText, memberNames) {
        ExpenseVoiceParser.parse(spokenText, memberNames)
    }

    // Speech Recognizer Intent Launcher
    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isListening = false
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenMatches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = spokenMatches?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                spokenText = spoken
            }
        }
    }

    fun launchVoiceRecognition() {
        try {
            isListening = true
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Di el gasto, monto y división: ej. 'Cena en Mario 65 dólares dividido en partes iguales'")
            }
            speechRecognizerLauncher.launch(intent)
        } catch (e: Exception) {
            isListening = false
            // Speech recognizer not available on device/emulator; user can still type or pick suggestions
        }
    }

    // Pulse animation for mic button
    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .testTag("voice_expense_dialog"),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Hablar para Ingresar Gasto",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("voice_close_button")) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Microphone Animated Trigger
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(110.dp)
                ) {
                    if (isListening) {
                        Box(
                            modifier = Modifier
                                .size(95.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(if (isListening) Color(0xFFEF4444) else MaterialTheme.colorScheme.primary)
                            .clickable { launchVoiceRecognition() }
                            .testTag("mic_record_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Hablar Gasto",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Text(
                    text = if (isListening) "Escuchando... Di tu gasto" else "Toca el micrófono para hablar o escribe abajo",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (isListening) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Editable transcript text
                OutlinedTextField(
                    value = spokenText,
                    onValueChange = { spokenText = it },
                    label = { Text("Texto dictado o escrito") },
                    placeholder = { Text("ej. Cena $50 partes iguales") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("voice_text_input"),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 2,
                    maxLines = 3
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Quick test suggestion chips
                Text(
                    text = "Plantillas Rápidas de Voz:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val suggestions = listOf(
                        "Cena en Mario $64 partes iguales",
                        "Alquiler $600 proporcional a ingresos",
                        "Supermercado $95 pagado por Alex al 50 50",
                        "Uber al aeropuerto $36 2 partes Alex 1 parte Yo",
                        "Internet alta velocidad $80 categoría Servicios",
                        "Café y medialunas $12"
                    )
                    suggestions.forEach { prompt ->
                        SuggestionChip(
                            onClick = { spokenText = prompt },
                            label = { Text(prompt, fontSize = 11.sp) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Real-time Parsed Preview Card
                AnimatedVisibility(visible = spokenText.isNotBlank()) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                            .testTag("voice_parsed_card")
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Gasto Detectado",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "$currencySymbol${String.format(Locale.US, "%.2f", parsedResult.amount)}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CategoryIconBadge(category = parsedResult.category, size = 32.dp, iconSize = 18.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = parsedResult.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Categoría: ${parsedResult.category.title} • División: ${parsedResult.splitType.label}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (parsedResult.payerName != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Pagador detectado: ${parsedResult.payerName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { onEditInForm(parsedResult) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Personalizar")
                    }

                    Button(
                        onClick = {
                            if (parsedResult.amount > 0) {
                                val payer = members.find {
                                    parsedResult.payerName != null && it.name.equals(parsedResult.payerName, ignoreCase = true)
                                } ?: members.find { it.isCurrentUser } ?: members.first()

                                // Compute splits
                                val splits = mutableListOf<MemberSplit>()
                                if (parsedResult.splitType == SplitType.PROPORTIONAL_INCOME) {
                                    val totalEarn = members.sumOf { memberMonthlyEarnings[it.id] ?: 0.0 }
                                    for (m in members) {
                                        val earn = memberMonthlyEarnings[m.id] ?: 0.0
                                        val pct = if (totalEarn > 0) (earn / totalEarn) * 100.0 else (if (members.isNotEmpty()) 100.0 / members.size else 0.0)
                                        val amt = (parsedResult.amount * pct) / 100.0
                                        splits.add(
                                            MemberSplit(
                                                memberId = m.id,
                                                memberName = m.name,
                                                shareValue = pct,
                                                computedAmount = amt
                                            )
                                        )
                                    }
                                } else {
                                    val count = members.size.coerceAtLeast(1)
                                    val equalAmt = parsedResult.amount / count

                                    for (m in members) {
                                        splits.add(
                                            MemberSplit(
                                                memberId = m.id,
                                                memberName = m.name,
                                                shareValue = 1.0,
                                                computedAmount = equalAmt
                                            )
                                        )
                                    }
                                }

                                onQuickSave(
                                    parsedResult.title,
                                    parsedResult.amount,
                                    parsedResult.category.title,
                                    payer.id,
                                    System.currentTimeMillis(),
                                    "Registrado por voz: \"$spokenText\"",
                                    parsedResult.splitType,
                                    splits
                                )
                            }
                        },
                        enabled = parsedResult.amount > 0,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("voice_quick_save_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Guardado Rápido")
                    }
                }
            }
        }
    }
}
