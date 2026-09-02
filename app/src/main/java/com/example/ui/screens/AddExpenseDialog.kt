package com.example.ui.screens

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.ExpenseCategory
import com.example.data.model.Member
import com.example.data.model.MemberSplit
import com.example.data.model.SplitType
import com.example.ui.components.CategoryIconBadge
import com.example.ui.components.MemberAvatarBadge
import com.example.ui.components.parseColorHex
import com.example.voice.ParsedVoiceExpense
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddExpenseDialog(
    members: List<Member>,
    currencySymbol: String = "$",
    initialVoiceData: ParsedVoiceExpense? = null,
    memberMonthlyEarnings: Map<Long, Double> = emptyMap(),
    onDismiss: () -> Unit,
    onSave: (
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
    var title by remember { mutableStateOf(initialVoiceData?.title ?: "") }
    var amountText by remember {
        mutableStateOf(
            if (initialVoiceData != null && initialVoiceData.amount > 0)
                String.format(Locale.US, "%.2f", initialVoiceData.amount)
            else ""
        )
    }
    var selectedCategory by remember {
        mutableStateOf(initialVoiceData?.category ?: ExpenseCategory.FOOD)
    }

    // Payer: default to current user or first member or voice detected payer
    val defaultPayer = remember(members, initialVoiceData) {
        if (initialVoiceData?.payerName != null) {
            members.find { it.name.equals(initialVoiceData.payerName, ignoreCase = true) }
        } else {
            members.find { it.isCurrentUser } ?: members.firstOrNull()
        }
    }
    var selectedPayerId by remember { mutableLongStateOf(defaultPayer?.id ?: 0L) }

    var selectedSplitType by remember {
        mutableStateOf(initialVoiceData?.splitType ?: SplitType.EQUAL)
    }

    var selectedDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var notes by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Equal split: which members are included
    val equalIncluded = remember { mutableStateMapOf<Long, Boolean>() }
    // Proportional to income split: which members are included
    val proportionalIncomeIncluded = remember { mutableStateMapOf<Long, Boolean>() }
    // Percentages: memberId -> percentage (0..100)
    val percentages = remember { mutableStateMapOf<Long, Double>() }
    // Parts: memberId -> share parts (1, 2, 3...)
    val parts = remember { mutableStateMapOf<Long, Double>() }
    // Exact amounts: memberId -> dollar amount
    val exactAmounts = remember { mutableStateMapOf<Long, Double>() }

    // Initialize allocations on start or when members change
    LaunchedEffect(members, memberMonthlyEarnings) {
        if (members.isNotEmpty()) {
            val count = members.size.toDouble()
            val equalPct = if (count > 0) 100.0 / count else 0.0
            val totalEarnings = memberMonthlyEarnings.values.sum()

            members.forEach { m ->
                equalIncluded[m.id] = true
                proportionalIncomeIncluded[m.id] = true
                parts[m.id] = 1.0

                // Check initial voice data allocations if present
                if (initialVoiceData != null && initialVoiceData.customAllocations.containsKey(m.name)) {
                    val allocVal = initialVoiceData.customAllocations[m.name] ?: 0.0
                    percentages[m.id] = allocVal
                    parts[m.id] = allocVal.coerceAtLeast(1.0)
                } else {
                    if (totalEarnings > 0) {
                        val mEarn = memberMonthlyEarnings[m.id] ?: 0.0
                        percentages[m.id] = (mEarn / totalEarnings) * 100.0
                    } else {
                        percentages[m.id] = equalPct
                    }
                }
                exactAmounts[m.id] = 0.0
            }
        }
    }

    val currentTotalAmount = amountText.toDoubleOrNull() ?: 0.0

    // Compute preview splits based on selected split type
    fun computeSplits(): List<MemberSplit> {
        val result = mutableListOf<MemberSplit>()
        when (selectedSplitType) {
            SplitType.EQUAL -> {
                val included = members.filter { equalIncluded[it.id] == true }
                val perPerson = if (included.isNotEmpty() && currentTotalAmount > 0) {
                    currentTotalAmount / included.size
                } else 0.0

                for (m in members) {
                    val isInc = equalIncluded[m.id] == true
                    result.add(
                        MemberSplit(
                            memberId = m.id,
                            memberName = m.name,
                            shareValue = if (isInc) 1.0 else 0.0,
                            computedAmount = if (isInc) perPerson else 0.0
                        )
                    )
                }
            }

            SplitType.PROPORTIONAL_INCOME -> {
                val included = members.filter { proportionalIncomeIncluded[it.id] == true }
                val totalIncludedEarnings = included.sumOf { memberMonthlyEarnings[it.id] ?: 0.0 }

                for (m in members) {
                    val isInc = proportionalIncomeIncluded[m.id] == true
                    if (!isInc) {
                        result.add(
                            MemberSplit(
                                memberId = m.id,
                                memberName = m.name,
                                shareValue = 0.0,
                                computedAmount = 0.0
                            )
                        )
                    } else {
                        val earning = memberMonthlyEarnings[m.id] ?: 0.0
                        val pct = if (totalIncludedEarnings > 0) {
                            (earning / totalIncludedEarnings) * 100.0
                        } else {
                            if (included.isNotEmpty()) 100.0 / included.size else 0.0
                        }
                        val computed = (currentTotalAmount * pct) / 100.0
                        result.add(
                            MemberSplit(
                                memberId = m.id,
                                memberName = m.name,
                                shareValue = pct,
                                computedAmount = computed
                            )
                        )
                    }
                }
            }

            SplitType.PERCENTAGE -> {
                for (m in members) {
                    val pct = percentages[m.id] ?: 0.0
                    val computed = (currentTotalAmount * pct) / 100.0
                    result.add(
                        MemberSplit(
                            memberId = m.id,
                            memberName = m.name,
                            shareValue = pct,
                            computedAmount = computed
                        )
                    )
                }
            }

            SplitType.PARTS -> {
                val totalParts = members.sumOf { parts[it.id] ?: 1.0 }.coerceAtLeast(1.0)
                for (m in members) {
                    val partCount = parts[m.id] ?: 1.0
                    val computed = (currentTotalAmount * partCount) / totalParts
                    result.add(
                        MemberSplit(
                            memberId = m.id,
                            memberName = m.name,
                            shareValue = partCount,
                            computedAmount = computed
                        )
                    )
                }
            }

            SplitType.EXACT_AMOUNTS -> {
                for (m in members) {
                    val exact = exactAmounts[m.id] ?: 0.0
                    result.add(
                        MemberSplit(
                            memberId = m.id,
                            memberName = m.name,
                            shareValue = exact,
                            computedAmount = exact
                        )
                    )
                }
            }
        }
        return result
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .testTag("add_expense_dialog"),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Registrar Gasto del Grupo",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Divide con miembros y gestiona balances",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_dialog_button")) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Title Input
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título / Concepto del gasto") },
                    placeholder = { Text("ej. Cena en Mario's, Uber, Supermercado") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("expense_title_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Amount Input
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        // Allow valid decimal characters
                        if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                            amountText = it
                        }
                    },
                    label = { Text("Monto Total") },
                    prefix = { Text(currencySymbol, fontWeight = FontWeight.Bold) },
                    placeholder = { Text("0.00") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("expense_amount_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Category Section
                Text(
                    text = "Categoría",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ExpenseCategory.entries.forEach { category ->
                        val isSelected = selectedCategory == category
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCategory = category },
                            label = { Text(category.title, fontSize = 12.sp) },
                            leadingIcon = {
                                CategoryIconBadge(category = category, size = 24.dp, iconSize = 14.dp)
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = parseColorHex(category.colorHex).copy(alpha = 0.2f),
                                selectedLabelColor = parseColorHex(category.colorHex)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Paid By Section
                Text(
                    text = "Pagado Por",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    members.forEach { member ->
                        val isPayer = selectedPayerId == member.id
                        FilterChip(
                            selected = isPayer,
                            onClick = { selectedPayerId = member.id },
                            label = { Text(if (member.isCurrentUser) "Tú" else member.name) },
                            leadingIcon = {
                                MemberAvatarBadge(name = member.name, colorHex = member.avatarColorHex, size = 22.dp)
                            },
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Bill Splitting Tabs
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Método de División",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val splitTypes = SplitType.entries
                        ScrollableTabRow(
                            selectedTabIndex = splitTypes.indexOf(selectedSplitType).coerceAtLeast(0),
                            containerColor = Color.Transparent,
                            divider = {},
                            edgePadding = 0.dp
                        ) {
                            splitTypes.forEach { type ->
                                Tab(
                                    selected = selectedSplitType == type,
                                    onClick = { selectedSplitType = type },
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (type == SplitType.PROPORTIONAL_INCOME) {
                                                Icon(
                                                    Icons.Default.Calculate,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(13.dp),
                                                    tint = if (selectedSplitType == type) Color(0xFF0F766E) else Color.Gray
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                            }
                                            Text(
                                                type.label,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (type == SplitType.PROPORTIONAL_INCOME && selectedSplitType == type) Color(0xFF0F766E) else Color.Unspecified
                                            )
                                        }
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Render active split type configuration
                        when (selectedSplitType) {
                            SplitType.PROPORTIONAL_INCOME -> {
                                val included = members.filter { proportionalIncomeIncluded[it.id] == true }
                                val totalInc = included.sumOf { memberMonthlyEarnings[it.id] ?: 0.0 }

                                Surface(
                                    color = Color(0xFF0F766E).copy(alpha = 0.10f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Icon(
                                            Icons.Default.Info,
                                            contentDescription = null,
                                            tint = Color(0xFF0F766E),
                                            modifier = Modifier
                                                .size(16.dp)
                                                .padding(top = 2.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = "División Proporcional a Ganancias",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = Color(0xFF0F766E)
                                            )
                                            Text(
                                                text = if (totalInc > 0)
                                                    "Cada miembro paga proporcional a sus ingresos de este mes (Total ganancias: $currencySymbol${String.format(Locale.US, "%.2f", totalInc)})."
                                                else
                                                    "No hay ganancias registradas en el mes. Se repartirá equitativamente hasta fijar ingresos en la pestaña Ingresos.",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Toca para incluir o excluir miembros de este gasto:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                members.forEach { m ->
                                    val isInc = proportionalIncomeIncluded[m.id] ?: true
                                    val earning = memberMonthlyEarnings[m.id] ?: 0.0
                                    val pct = if (isInc && totalInc > 0) {
                                        (earning / totalInc) * 100.0
                                    } else if (isInc && included.isNotEmpty()) {
                                        100.0 / included.size
                                    } else 0.0
                                    val share = (currentTotalAmount * pct) / 100.0

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isInc) Color(0xFF0F766E).copy(alpha = 0.08f) else Color.Transparent)
                                            .clickable { proportionalIncomeIncluded[m.id] = !isInc }
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(22.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isInc) Color(0xFF0F766E) else Color.LightGray),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isInc) Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(m.name, fontWeight = if (isInc) FontWeight.SemiBold else FontWeight.Normal)
                                                Text(
                                                    text = "Gana $currencySymbol${String.format(Locale.US, "%.2f", earning)} • ${String.format(Locale.US, "%.1f", pct)}%",
                                                    fontSize = 11.sp,
                                                    color = if (isInc) Color(0xFF0F766E) else Color.Gray,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                        Text(
                                            text = "$currencySymbol${String.format(Locale.US, "%.2f", share)}",
                                            fontWeight = FontWeight.Bold,
                                            color = if (isInc) Color(0xFF0F766E) else Color.Gray
                                        )
                                    }
                                }
                            }

                            SplitType.EQUAL -> {
                                Text(
                                    text = "Toca miembros para incluir o excluir de la división equitativa:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                members.forEach { m ->
                                    val isInc = equalIncluded[m.id] ?: true
                                    val incCount = members.count { equalIncluded[it.id] == true }.coerceAtLeast(1)
                                    val share = if (isInc && currentTotalAmount > 0) currentTotalAmount / incCount else 0.0

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isInc) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent)
                                            .clickable { equalIncluded[m.id] = !isInc }
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(22.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isInc) MaterialTheme.colorScheme.primary else Color.LightGray),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isInc) Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(m.name, fontWeight = if (isInc) FontWeight.SemiBold else FontWeight.Normal)
                                        }
                                        Text(
                                            text = "$currencySymbol${String.format(Locale.US, "%.2f", share)}",
                                            fontWeight = FontWeight.Bold,
                                            color = if (isInc) MaterialTheme.colorScheme.primary else Color.Gray
                                        )
                                    }
                                }
                            }

                            SplitType.PERCENTAGE -> {
                                val totalPct = members.sumOf { percentages[it.id] ?: 0.0 }
                                val is100 = abs(totalPct - 100.0) < 0.1

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = "Asignar % a cada miembro:", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        text = "Total: ${String.format(Locale.US, "%.1f", totalPct)}%",
                                        fontWeight = FontWeight.Bold,
                                        color = if (is100) Color(0xFF10B981) else Color(0xFFEF4444),
                                        fontSize = 12.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))

                                if (memberMonthlyEarnings.values.sum() > 0) {
                                    Surface(
                                        color = Color(0xFF0F766E).copy(alpha = 0.08f),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                val totalEarnings = memberMonthlyEarnings.values.sum()
                                                if (totalEarnings > 0) {
                                                    members.forEach { mem ->
                                                        val mEarn = memberMonthlyEarnings[mem.id] ?: 0.0
                                                        percentages[mem.id] = (mEarn / totalEarnings) * 100.0
                                                    }
                                                }
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Calculate,
                                                contentDescription = null,
                                                tint = Color(0xFF0F766E),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Aplicar % de ganancias del mes",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF0F766E)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                }

                                members.forEach { m ->
                                    val pct = percentages[m.id] ?: 0.0
                                    val computed = (currentTotalAmount * pct) / 100.0
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(m.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = { percentages[m.id] = (pct - 5.0).coerceAtLeast(0.0) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(Icons.Default.Remove, contentDescription = "Disminuir", modifier = Modifier.size(16.dp))
                                            }
                                            Text(
                                                text = "${pct.toInt()}%",
                                                modifier = Modifier.width(42.dp),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            IconButton(
                                                onClick = { percentages[m.id] = (pct + 5.0).coerceAtMost(100.0) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = "Aumentar", modifier = Modifier.size(16.dp))
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "$currencySymbol${String.format(Locale.US, "%.2f", computed)}",
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                }
                            }

                            SplitType.PARTS -> {
                                val totalParts = members.sumOf { parts[it.id] ?: 1.0 }.coerceAtLeast(1.0)
                                Text(
                                    text = "Asignar partes/cuotas (ej. 2 partes vs 1 parte):",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                members.forEach { m ->
                                    val partVal = parts[m.id] ?: 1.0
                                    val computed = (currentTotalAmount * partVal) / totalParts
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(m.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = { parts[m.id] = (partVal - 1.0).coerceAtLeast(0.0) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(Icons.Default.Remove, contentDescription = "Disminuir", modifier = Modifier.size(16.dp))
                                            }
                                            Text(
                                                text = "${partVal.toInt()} part",
                                                modifier = Modifier.width(44.dp),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            IconButton(
                                                onClick = { parts[m.id] = partVal + 1.0 },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = "Aumentar", modifier = Modifier.size(16.dp))
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "$currencySymbol${String.format(Locale.US, "%.2f", computed)}",
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                }
                            }

                            SplitType.EXACT_AMOUNTS -> {
                                val totalAssigned = members.sumOf { exactAmounts[it.id] ?: 0.0 }
                                val diff = currentTotalAmount - totalAssigned
                                val isBalanced = abs(diff) < 0.01

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = "Especificar montos exactos:", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        text = if (isBalanced) "Cuadrado ✓" else "Restante: $currencySymbol${String.format(Locale.US, "%.2f", diff)}",
                                        fontWeight = FontWeight.Bold,
                                        color = if (isBalanced) Color(0xFF10B981) else Color(0xFFEF4444),
                                        fontSize = 12.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))

                                members.forEach { m ->
                                    var textVal by remember(m.id) {
                                        mutableStateOf(String.format(Locale.US, "%.2f", exactAmounts[m.id] ?: 0.0))
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(m.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                                        OutlinedTextField(
                                            value = textVal,
                                            onValueChange = {
                                                if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                                                    textVal = it
                                                    exactAmounts[m.id] = it.toDoubleOrNull() ?: 0.0
                                                }
                                            },
                                            prefix = { Text(currencySymbol) },
                                            modifier = Modifier.width(110.dp),
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Date Picker Quick Selectors
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val cal = Calendar.getInstance()
                    val todayMillis = cal.timeInMillis
                    cal.add(Calendar.DAY_OF_YEAR, -1)
                    val yesterdayMillis = cal.timeInMillis

                    FilterChip(
                        selected = abs(selectedDateMillis - todayMillis) < 43200000L,
                        onClick = { selectedDateMillis = System.currentTimeMillis() },
                        label = { Text("Hoy") }
                    )
                    FilterChip(
                        selected = abs(selectedDateMillis - yesterdayMillis) < 43200000L,
                        onClick = { selectedDateMillis = yesterdayMillis },
                        label = { Text("Ayer") }
                    )
                    Text(
                        text = SimpleDateFormat("d 'de' MMM, yyyy", Locale("es", "ES")).format(Date(selectedDateMillis)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Notes
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notas (Opcional)") },
                    placeholder = { Text("Detalles del recibo, artículos comprados, etc.") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val parsedAmt = amountText.toDoubleOrNull() ?: 0.0
                            if (parsedAmt <= 0.0) {
                                errorMessage = "Por favor ingresa un monto válido mayor a 0."
                                return@Button
                            }
                            val splits = computeSplits()
                            onSave(
                                title.ifBlank { selectedCategory.title },
                                parsedAmt,
                                selectedCategory.title,
                                selectedPayerId,
                                selectedDateMillis,
                                notes,
                                selectedSplitType,
                                splits
                            )
                        },
                        modifier = Modifier.testTag("save_expense_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Guardar Gasto", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
