package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.Group
import com.example.ui.screens.AddExpenseDialog
import com.example.ui.screens.BalancesDialog
import com.example.ui.screens.ExpensesScreen
import com.example.ui.screens.ExportSyncScreen
import com.example.ui.screens.IncomeScreen
import com.example.ui.screens.InvestmentsScreen
import com.example.ui.screens.MonthlyResumeScreen
import com.example.ui.screens.VoiceExpenseDialog
import com.example.ui.viewmodel.GroupExpenseViewModel
import com.example.ui.viewmodel.MemberMonthlyIncome
import com.example.voice.ParsedVoiceExpense

enum class AppTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    EXPENSES("Gastos", Icons.Default.ReceiptLong),
    MONTHLY_RESUME("Resumen", Icons.Default.Assessment),
    INCOME("Ingresos", Icons.Default.MonetizationOn),
    INVESTMENTS("Inversiones", Icons.Default.TrendingUp),
    EXPORT_SYNC("Sincronizar", Icons.Default.SyncAlt)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: GroupExpenseViewModel = viewModel()) {
    var selectedTab by remember { mutableStateOf(AppTab.EXPENSES) }

    val groups by viewModel.groups.collectAsState()
    val selectedGroup by viewModel.selectedGroup.collectAsState()
    val members by viewModel.members.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val dateExpenseGroups by viewModel.expensesGroupedByDate.collectAsState()
    val memberBalances by viewModel.memberBalances.collectAsState()
    val debtSettlements by viewModel.debtSettlements.collectAsState()
    val monthlyStats by viewModel.monthlyStats.collectAsState()
    val incomes by viewModel.incomes.collectAsState()
    val monthlyMemberIncomes by viewModel.monthlyMemberIncomes.collectAsState()
    val selectedIncomeMonth by viewModel.selectedIncomeMonth.collectAsState()
    val groupMonthlyTotalIncome by viewModel.groupMonthlyTotalIncome.collectAsState()
    val investments by viewModel.investments.collectAsState()
    val pendingSyncCount by viewModel.pendingSyncCount.collectAsState()

    val currentMemberMonthlyEarnings = remember(monthlyMemberIncomes) {
        monthlyMemberIncomes.associate { it.member.id to it.totalIncome }
    }

    var showAddExpenseDialog by remember { mutableStateOf(false) }
    var showVoiceExpenseDialog by remember { mutableStateOf(false) }
    var showBalancesDialog by remember { mutableStateOf(false) }
    var voicePreFillData by remember { mutableStateOf<ParsedVoiceExpense?>(null) }
    var showGroupDropdown by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "casa rl.",
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        if (groups.isNotEmpty()) {
                            Box {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { showGroupDropdown = true }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = selectedGroup?.name ?: "Seleccionar Grupo",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 11.sp
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = "Cambiar grupo",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = showGroupDropdown,
                                    onDismissRequest = { showGroupDropdown = false }
                                ) {
                                    groups.forEach { grp ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = grp.name,
                                                    fontWeight = if (grp.id == selectedGroup?.id) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            onClick = {
                                                viewModel.selectGroup(grp)
                                                showGroupDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                actions = {
                    // Offline sync indicator badge in top bar
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (pendingSyncCount > 0) Color(0xFFF59E0B).copy(alpha = 0.2f) else Color(0xFF10B981).copy(alpha = 0.2f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (pendingSyncCount > 0) Icons.Default.CloudSync else Icons.Default.CloudDone,
                                    contentDescription = "Estado de sincronización",
                                    tint = if (pendingSyncCount > 0) Color(0xFFD97706) else Color(0xFF059669),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (pendingSyncCount > 0) "$pendingSyncCount locales" else "Sincronizado",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (pendingSyncCount > 0) Color(0xFFD97706) else Color(0xFF059669)
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier.testTag("bottom_navigation_bar")
            ) {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                fontSize = 10.sp,
                                fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                AppTab.EXPENSES -> {
                    ExpensesScreen(
                        currentGroup = selectedGroup,
                        members = members,
                        dateExpenseGroups = dateExpenseGroups,
                        balances = memberBalances,
                        currencySymbol = selectedGroup?.currencySymbol ?: "$",
                        onAddExpenseClick = {
                            voicePreFillData = null
                            showAddExpenseDialog = true
                        },
                        onVoiceExpenseClick = {
                            showVoiceExpenseDialog = true
                        },
                        onBalancesClick = {
                            showBalancesDialog = true
                        },
                        onDeleteExpense = { exp ->
                            viewModel.deleteExpense(exp)
                        }
                    )
                }

                AppTab.MONTHLY_RESUME -> {
                    MonthlyResumeScreen(
                        stats = monthlyStats,
                        currencySymbol = selectedGroup?.currencySymbol ?: "$",
                        onPrevMonth = { viewModel.navigateMonth(-1) },
                        onNextMonth = { viewModel.navigateMonth(1) }
                    )
                }

                AppTab.INCOME -> {
                    IncomeScreen(
                        members = members,
                        incomes = incomes,
                        monthlyMemberIncomes = monthlyMemberIncomes,
                        selectedIncomeMonth = selectedIncomeMonth,
                        groupMonthlyTotalIncome = groupMonthlyTotalIncome,
                        currencySymbol = selectedGroup?.currencySymbol ?: "$",
                        onPrevMonth = { viewModel.navigateIncomeMonth(-1) },
                        onNextMonth = { viewModel.navigateIncomeMonth(1) },
                        onSetMemberMonthlyIncome = { memberId, memberName, amount ->
                            viewModel.setMemberMonthlyIncome(memberId, memberName, amount, selectedIncomeMonth)
                        },
                        onSetGroupMonthlyEarnings = { earningsMap ->
                            viewModel.setGroupMonthlyEarnings(earningsMap, selectedIncomeMonth)
                        },
                        onAddIncome = { t, a, s, d, n, mId, mName ->
                            viewModel.saveIncome(t, a, s, d, n, mId, mName)
                        },
                        onDeleteIncome = { inc ->
                            viewModel.deleteIncome(inc)
                        }
                    )
                }

                AppTab.INVESTMENTS -> {
                    InvestmentsScreen(
                        investments = investments,
                        currencySymbol = selectedGroup?.currencySymbol ?: "$",
                        onAddInvestment = { name, symbol, type, inv, v, qty, notes ->
                            viewModel.saveInvestment(name, symbol, type, inv, v, qty, notes)
                        },
                        onUpdateInvestment = { inv ->
                            viewModel.updateInvestment(inv)
                        },
                        onDeleteInvestment = { inv ->
                            viewModel.deleteInvestment(inv)
                        }
                    )
                }

                AppTab.EXPORT_SYNC -> {
                    ExportSyncScreen(
                        currentGroup = selectedGroup,
                        groups = groups,
                        members = members,
                        expenses = expenses,
                        incomes = incomes,
                        investments = investments,
                        pendingSyncCount = pendingSyncCount,
                        onSelectGroup = { grp -> viewModel.selectGroup(grp) },
                        onCreateGroup = { name, desc -> viewModel.createGroup(name, desc) },
                        onAddMember = { name, color -> viewModel.addMember(name, color) },
                        onDeleteMember = { mem -> viewModel.deleteMember(mem) },
                        onSyncNow = { onDone -> viewModel.syncAll(onDone) }
                    )
                }
            }

            // Hosted Dialogs
            if (showAddExpenseDialog) {
                AddExpenseDialog(
                    members = members,
                    currencySymbol = selectedGroup?.currencySymbol ?: "$",
                    initialVoiceData = voicePreFillData,
                    memberMonthlyEarnings = currentMemberMonthlyEarnings,
                    onDismiss = {
                        showAddExpenseDialog = false
                        voicePreFillData = null
                    },
                    onSave = { title, amt, cat, payer, date, notes, splitType, splits ->
                        viewModel.saveExpense(title, amt, cat, payer, date, notes, splitType, splits)
                        showAddExpenseDialog = false
                        voicePreFillData = null
                    }
                )
            }

            if (showVoiceExpenseDialog) {
                VoiceExpenseDialog(
                    members = members,
                    currencySymbol = selectedGroup?.currencySymbol ?: "$",
                    memberMonthlyEarnings = currentMemberMonthlyEarnings,
                    onDismiss = { showVoiceExpenseDialog = false },
                    onEditInForm = { parsed ->
                        showVoiceExpenseDialog = false
                        voicePreFillData = parsed
                        showAddExpenseDialog = true
                    },
                    onQuickSave = { title, amt, cat, payer, date, notes, splitType, splits ->
                        viewModel.saveExpense(title, amt, cat, payer, date, notes, splitType, splits)
                        showVoiceExpenseDialog = false
                    }
                )
            }

            if (showBalancesDialog) {
                BalancesDialog(
                    balances = memberBalances,
                    settlements = debtSettlements,
                    currencySymbol = selectedGroup?.currencySymbol ?: "$",
                    onDismiss = { showBalancesDialog = false }
                )
            }
        }
    }
}
