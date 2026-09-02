package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LaptopMac
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.Income
import com.example.data.model.Member
import com.example.ui.components.MemberAvatarBadge
import com.example.ui.components.parseColorHex
import com.example.ui.viewmodel.MemberMonthlyIncome
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun getIncomeSourceIcon(source: String): ImageVector {
    return when (source.lowercase()) {
        "salary", "sueldo", "salario" -> Icons.Default.Payments
        "freelance", "independiente" -> Icons.Default.LaptopMac
        "business", "negocio" -> Icons.Default.BusinessCenter
        "investments", "investment", "inversiones", "inversión" -> Icons.Default.TrendingUp
        "gift", "bonus", "bono", "regalo" -> Icons.Default.CardGiftcard
        else -> Icons.Default.MonetizationOn
    }
}

@Composable
fun IncomeScreen(
    members: List<Member>,
    incomes: List<Income>,
    monthlyMemberIncomes: List<MemberMonthlyIncome>,
    selectedIncomeMonth: Calendar,
    groupMonthlyTotalIncome: Double,
    currencySymbol: String = "$",
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSetMemberMonthlyIncome: (memberId: Long, memberName: String, amount: Double) -> Unit,
    onSetGroupMonthlyEarnings: (earningsMap: Map<Long, Double>) -> Unit,
    onAddIncome: (title: String, amount: Double, source: String, date: Long, notes: String, memberId: Long?, memberName: String) -> Unit,
    onDeleteIncome: (Income) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var showQuickSetAllDialog by remember { mutableStateOf(false) }
    var memberToEditIncome by remember { mutableStateOf<Member?>(null) }
    var incomeToDelete by remember { mutableStateOf<Income?>(null) }
    var showOnlyThisMonth by remember { mutableStateOf(true) }

    val spanishLocale = Locale("es", "ES")
    val monthTitle = remember(selectedIncomeMonth.timeInMillis) {
        val raw = SimpleDateFormat("MMMM yyyy", spanishLocale).format(selectedIncomeMonth.time)
        raw.replaceFirstChar { if (it.isLowerCase()) it.titlecase(spanishLocale) else it.toString() }
    }

    val isCurrentMonth = remember(selectedIncomeMonth.timeInMillis) {
        val nowCal = Calendar.getInstance()
        nowCal.get(Calendar.YEAR) == selectedIncomeMonth.get(Calendar.YEAR) &&
                nowCal.get(Calendar.MONTH) == selectedIncomeMonth.get(Calendar.MONTH)
    }

    // Filter incomes for selected month
    val filteredIncomes = remember(incomes, selectedIncomeMonth.timeInMillis, showOnlyThisMonth) {
        if (!showOnlyThisMonth) incomes
        else {
            val targetYear = selectedIncomeMonth.get(Calendar.YEAR)
            val targetMonth = selectedIncomeMonth.get(Calendar.MONTH)
            val cal = Calendar.getInstance()
            incomes.filter { inc ->
                cal.timeInMillis = inc.date
                cal.get(Calendar.YEAR) == targetYear && cal.get(Calendar.MONTH) == targetMonth
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("income_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Month Navigation Header
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onPrevMonth, modifier = Modifier.testTag("prev_income_month")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Mes Anterior")
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = monthTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (isCurrentMonth) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = Color(0xFF10B981).copy(alpha = 0.18f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "Actual",
                                        color = Color(0xFF047857),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        IconButton(onClick = onNextMonth, modifier = Modifier.testTag("next_income_month")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Mes Siguiente")
                        }
                    }
                }
            }

            // 2. Proportional Distribution Highlight Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F766E).copy(alpha = 0.08f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Percent,
                                        contentDescription = null,
                                        tint = Color(0xFF0F766E),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Ganancias Totales del Mes",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "$currencySymbol${String.format(Locale.US, "%.2f", groupMonthlyTotalIncome)}",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF0F766E)
                                )
                                Text(
                                    text = "Base de cálculo para división proporcional de gastos",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF0F766E)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Calculate,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Multi-segment Proportional Distribution Bar
                        Text(
                            text = "Distribución Porcentual entre Miembros:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        if (monthlyMemberIncomes.isNotEmpty() && groupMonthlyTotalIncome > 0) {
                            // Segmented bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(Color.LightGray.copy(alpha = 0.3f))
                            ) {
                                monthlyMemberIncomes.forEach { mi ->
                                    val pctWeight = (mi.percentage / 100.0).toFloat().coerceAtLeast(0.001f)
                                    val memberColor = parseColorHex(mi.member.avatarColorHex)
                                    Box(
                                        modifier = Modifier
                                            .weight(pctWeight)
                                            .fillMaxSize()
                                            .background(memberColor)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Mini legend under bar
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                monthlyMemberIncomes.forEach { mi ->
                                    val memberColor = parseColorHex(mi.member.avatarColorHex)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(memberColor)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${mi.member.name}: ${String.format(Locale.US, "%.1f", mi.percentage)}%",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = "Aún no hay ganancias fijadas este mes. Todos los miembros tendrán 50/50 o partes iguales hasta que se registren ingresos.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Explanatory Banner (User's specific rule)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = Color(0xFF0F766E),
                                    modifier = Modifier
                                        .size(18.dp)
                                        .padding(top = 2.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Los gastos divididos en modo 'Proporcional a Ganancias' se reparten según estos porcentajes. Ej: si A gana 10 y B gana 5, un gasto se dividirá para que A aporte el 66.7% y B el 33.3%.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 16.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Quick Set Button
                        Button(
                            onClick = { showQuickSetAllDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("quick_set_monthly_earnings_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E))
                        ) {
                            Icon(Icons.Default.Calculate, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Fijar Ganancias del Mes para el Grupo",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 3. Member Breakdown List
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Ganancias y Porcentajes por Miembro",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            items(monthlyMemberIncomes, key = { it.member.id }) { mi ->
                val memberColor = parseColorHex(mi.member.avatarColorHex)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("member_income_card_${mi.member.id}"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MemberAvatarBadge(
                                    name = mi.member.name,
                                    colorHex = mi.member.avatarColorHex,
                                    size = 40.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = mi.member.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (mi.member.isCurrentUser) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = "Tú",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = "Ganancia: $currencySymbol${String.format(Locale.US, "%.2f", mi.totalIncome)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Percentage Pill
                            Surface(
                                color = memberColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${String.format(Locale.US, "%.1f", mi.percentage)}%",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = memberColor
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "del total",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Individual Progress Bar
                        LinearProgressIndicator(
                            progress = { (mi.percentage / 100.0).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = memberColor,
                            trackColor = memberColor.copy(alpha = 0.2f),
                            strokeCap = StrokeCap.Round
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Edit Button for this member
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(
                                onClick = { memberToEditIncome = mi.member },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Editar Ganancia", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // 4. Detailed Income Records Section
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Entradas de Ingresos",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (showOnlyThisMonth) "Mostrando registros de $monthTitle" else "Mostrando todos los registros",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    FilterChip(
                        selected = showOnlyThisMonth,
                        onClick = { showOnlyThisMonth = !showOnlyThisMonth },
                        label = { Text(if (showOnlyThisMonth) "Solo este mes" else "Todos") },
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }

            if (filteredIncomes.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Payments,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Sin registros de ingresos en $monthTitle",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Fija las ganancias con el botón de arriba o registra un ingreso individual con el botón +",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(filteredIncomes, key = { it.id }) { income ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF10B981).copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = getIncomeSourceIcon(income.source),
                                        contentDescription = income.source,
                                        tint = Color(0xFF047857),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(
                                        text = income.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (income.memberName.isNotBlank()) {
                                            Text(
                                                text = income.memberName,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = " • ",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = income.source,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = " • " + SimpleDateFormat("d 'de' MMM, yyyy", spanishLocale).format(Date(income.date)),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "+$currencySymbol${String.format(Locale.US, "%.2f", income.amount)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF047857)
                                )
                                IconButton(
                                    onClick = { incomeToDelete = income },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Eliminar",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }

        // Floating Action Button to Add Income
        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("add_income_fab"),
            containerColor = Color(0xFF0F766E),
            contentColor = Color.White
        ) {
            Icon(Icons.Default.Add, contentDescription = "Registrar Ingreso")
        }
    }

    // Modal: Quick Set Monthly Earnings for all members (e.g. A = 10, B = 5)
    if (showQuickSetAllDialog) {
        QuickSetGroupEarningsDialog(
            members = members,
            currentIncomes = monthlyMemberIncomes,
            monthTitle = monthTitle,
            currencySymbol = currencySymbol,
            onDismiss = { showQuickSetAllDialog = false },
            onSave = { earningsMap ->
                onSetGroupMonthlyEarnings(earningsMap)
                showQuickSetAllDialog = false
            }
        )
    }

    // Modal: Set Monthly Earnings for single member
    if (memberToEditIncome != null) {
        val mem = memberToEditIncome!!
        val currentEarning = monthlyMemberIncomes.find { it.member.id == mem.id }?.totalIncome ?: 0.0
        SetMemberEarningsDialog(
            member = mem,
            monthTitle = monthTitle,
            initialAmount = currentEarning,
            currencySymbol = currencySymbol,
            onDismiss = { memberToEditIncome = null },
            onSave = { amount ->
                onSetMemberMonthlyIncome(mem.id, mem.name, amount)
                memberToEditIncome = null
            }
        )
    }

    // Modal: Add Detailed Income Entry
    if (showAddDialog) {
        AddIncomeDialog(
            members = members,
            defaultDate = selectedIncomeMonth.timeInMillis,
            currencySymbol = currencySymbol,
            onDismiss = { showAddDialog = false },
            onSave = { title, amount, source, date, notes, memberId, memberName ->
                onAddIncome(title, amount, source, date, notes, memberId, memberName)
                showAddDialog = false
            }
        )
    }

    // Delete Confirmation Dialog
    if (incomeToDelete != null) {
        AlertDialog(
            onDismissRequest = { incomeToDelete = null },
            title = { Text("Eliminar Registro de Ingreso") },
            text = { Text("¿Deseas eliminar '${incomeToDelete?.title}' de $currencySymbol${String.format(Locale.US, "%.2f", incomeToDelete?.amount ?: 0.0)}?") },
            confirmButton = {
                Button(
                    onClick = {
                        incomeToDelete?.let { onDeleteIncome(it) }
                        incomeToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { incomeToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickSetGroupEarningsDialog(
    members: List<Member>,
    currentIncomes: List<MemberMonthlyIncome>,
    monthTitle: String,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onSave: (Map<Long, Double>) -> Unit
) {
    val inputs = remember {
        mutableStateMapOf<Long, String>().apply {
            members.forEach { m ->
                val existing = currentIncomes.find { it.member.id == m.id }?.totalIncome ?: 0.0
                put(m.id, if (existing > 0) String.format(Locale.US, "%.2f", existing) else "")
            }
        }
    }

    val parsedAmounts = remember(inputs.values.toList()) {
        members.associate { m ->
            m.id to (inputs[m.id]?.toDoubleOrNull() ?: 0.0)
        }
    }

    val total = parsedAmounts.values.sum()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .clip(RoundedCornerShape(24.dp))
                .testTag("quick_set_group_earnings_dialog"),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Fijar Ganancias del Mes",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$monthTitle • Cálculo automático de %",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0F766E).copy(alpha = 0.08f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Total Grupo: $currencySymbol${String.format(Locale.US, "%.2f", total)}",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F766E)
                        )
                        Text(
                            text = "${members.size} miembros",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Member input rows
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    members.forEach { m ->
                        val amt = parsedAmounts[m.id] ?: 0.0
                        val pct = if (total > 0) (amt / total) * 100.0 else (if (members.isNotEmpty()) 100.0 / members.size else 0.0)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MemberAvatarBadge(name = m.name, colorHex = m.avatarColorHex, size = 36.dp)

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = m.name,
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "${String.format(Locale.US, "%.1f", pct)}% del total",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF0F766E),
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            OutlinedTextField(
                                value = inputs[m.id] ?: "",
                                onValueChange = { inputs[m.id] = it },
                                placeholder = { Text("0.00") },
                                prefix = { Text(currencySymbol) },
                                modifier = Modifier
                                    .width(130.dp)
                                    .testTag("earning_input_${m.id}"),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val resultMap = members.associate { m ->
                                m.id to (inputs[m.id]?.toDoubleOrNull() ?: 0.0)
                            }
                            onSave(resultMap)
                        },
                        modifier = Modifier.testTag("save_group_earnings_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Guardar y Fijar %", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun SetMemberEarningsDialog(
    member: Member,
    monthTitle: String,
    initialAmount: Double,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    var amountText by remember {
        mutableStateOf(if (initialAmount > 0) String.format(Locale.US, "%.2f", initialAmount) else "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ganancia de ${member.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Fijar ganancia mensual para $monthTitle:",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Monto de Ganancia") },
                    prefix = { Text(currencySymbol) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Este monto definirá la proporción en la que ${member.name} aportará a los gastos compartidos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = amountText.toDoubleOrNull() ?: 0.0
                    onSave(parsed)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E))
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddIncomeDialog(
    members: List<Member>,
    defaultDate: Long,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onSave: (title: String, amount: Double, source: String, date: Long, notes: String, memberId: Long?, memberName: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var selectedSource by remember { mutableStateOf("Sueldo") }
    var selectedMemberId by remember {
        mutableStateOf(members.find { it.isCurrentUser }?.id ?: members.firstOrNull()?.id)
    }
    var notes by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val sources = listOf("Sueldo", "Freelance", "Negocio", "Inversiones", "Bono", "Otro")

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .clip(RoundedCornerShape(24.dp))
                .testTag("add_income_dialog"),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Registrar Entrada de Ingreso",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                // Member Selector
                Text(
                    text = "¿A quién pertenece este ingreso?",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    members.forEach { m ->
                        val isSelected = selectedMemberId == m.id
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedMemberId = m.id },
                            label = { Text(if (m.isCurrentUser) "Tú" else m.name) },
                            leadingIcon = {
                                MemberAvatarBadge(name = m.name, colorHex = m.avatarColorHex, size = 20.dp)
                            },
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }

                // Amount
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        errorMessage = null
                    },
                    label = { Text("Monto del Ingreso *") },
                    prefix = { Text(currencySymbol) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(12.dp)
                )

                // Title
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Concepto / Descripción") },
                    placeholder = { Text("ej. Salario Mensual, Proyecto Freelance") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                // Source Chips
                Text(
                    text = "Tipo de Fuente:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sources.forEach { src ->
                        FilterChip(
                            selected = selectedSource == src,
                            onClick = { selectedSource = src },
                            label = { Text(src) },
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }

                // Notes
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notas (Opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val parsed = amountText.toDoubleOrNull() ?: 0.0
                            if (parsed <= 0.0) {
                                errorMessage = "Por favor ingresa un monto válido mayor a 0."
                                return@Button
                            }
                            val member = members.find { it.id == selectedMemberId }
                            val mName = member?.name ?: ""
                            onSave(
                                title.ifBlank { "Ingreso de $mName" },
                                parsed,
                                selectedSource,
                                defaultDate,
                                notes,
                                selectedMemberId,
                                mName
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Guardar Ingreso", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
