package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.Expense
import com.example.data.model.ExpenseCategory
import com.example.data.model.Group
import com.example.data.model.Income
import com.example.data.model.Investment
import com.example.data.model.Member
import com.example.data.model.MemberSplit
import com.example.data.model.SplitJsonConverter
import com.example.data.model.SplitType
import com.example.data.repository.DebtSettlement
import com.example.data.repository.ExpenseRepository
import com.example.data.repository.MemberBalance
import com.example.voice.ExpenseVoiceParser
import com.example.voice.ParsedVoiceExpense
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class DateExpenseGroup(
    val dateHeader: String,
    val totalAmount: Double,
    val expenses: List<Expense>
)

data class CategorySpend(
    val category: ExpenseCategory,
    val amount: Double,
    val percentage: Float
)

data class MonthlyResumeStats(
    val monthTitle: String,
    val totalGroupExpenses: Double,
    val userShareExpenses: Double,
    val totalIncome: Double,
    val netSavings: Double,
    val savingsRate: Double,
    val categorySpends: List<CategorySpend>,
    val memberSpends: List<Pair<String, Double>>,
    val expenseCount: Int
)

data class MemberMonthlyIncome(
    val member: Member,
    val totalIncome: Double,
    val percentage: Double
)

class GroupExpenseViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ExpenseRepository

    init {
        val db = AppDatabase.getDatabase(application)
        repository = ExpenseRepository(db)
        viewModelScope.launch {
            repository.checkAndSeedInitialData()
        }
    }

    val groups: StateFlow<List<Group>> = repository.allGroups
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedGroupId = MutableStateFlow<Long?>(null)
    val selectedGroupId = _selectedGroupId.asStateFlow()

    private val _selectedGroup = MutableStateFlow<Group?>(null)
    val selectedGroup = _selectedGroup.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val members: StateFlow<List<Member>> = _selectedGroupId.flatMapLatest { id ->
        if (id != null) repository.getMembersForGroup(id) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val expenses: StateFlow<List<Expense>> = _selectedGroupId.flatMapLatest { id ->
        if (id != null) repository.getExpensesForGroup(id) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allExpenses: StateFlow<List<Expense>> = repository.allExpenses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val incomes: StateFlow<List<Income>> = repository.allIncomes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val investments: StateFlow<List<Investment>> = repository.allInvestments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingSyncCount: StateFlow<Int> = repository.pendingSyncCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Automatically select the first group when groups load
    init {
        viewModelScope.launch {
            groups.collect { groupList ->
                if (groupList.isNotEmpty() && _selectedGroupId.value == null) {
                    _selectedGroupId.value = groupList.first().id
                    _selectedGroup.value = groupList.first()
                } else if (_selectedGroupId.value != null) {
                    _selectedGroup.value = groupList.find { it.id == _selectedGroupId.value }
                }
            }
        }
    }

    // Expenses Grouped by Date
    val expensesGroupedByDate: StateFlow<List<DateExpenseGroup>> = expenses.combine(members) { expenseList, _ ->
        val spanishLocale = Locale("es", "ES")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val displayFormat = SimpleDateFormat("EEEE, d 'de' MMMM, yyyy", spanishLocale)
        val todayStr = dateFormat.format(Date())
        val yesterdayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val yesterdayStr = dateFormat.format(yesterdayCal.time)

        val groupsMap = LinkedHashMap<String, MutableList<Expense>>()
        for (exp in expenseList) {
            val key = dateFormat.format(Date(exp.date))
            if (!groupsMap.containsKey(key)) {
                groupsMap[key] = mutableListOf()
            }
            groupsMap[key]?.add(exp)
        }

        groupsMap.map { (dateKey, list) ->
            val header = when (dateKey) {
                todayStr -> "Hoy • ${SimpleDateFormat("d 'de' MMM", spanishLocale).format(Date())}"
                yesterdayStr -> "Ayer • ${SimpleDateFormat("d 'de' MMM", spanishLocale).format(yesterdayCal.time)}"
                else -> {
                    try {
                        val parsed = dateFormat.parse(dateKey)
                        if (parsed != null) {
                            displayFormat.format(parsed).replaceFirstChar { if (it.isLowerCase()) it.titlecase(spanishLocale) else it.toString() }
                        } else {
                            dateKey
                        }
                    } catch (e: Exception) {
                        dateKey
                    }
                }
            }
            val total = list.sumOf { it.amount }
            DateExpenseGroup(dateHeader = header, totalAmount = total, expenses = list)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Balances and Settlements
    val memberBalances: StateFlow<List<MemberBalance>> = combine(members, expenses) { mList, eList ->
        repository.calculateBalances(mList, eList)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val debtSettlements: StateFlow<List<DebtSettlement>> = memberBalances.combine(members) { balances, _ ->
        repository.calculateSettlements(balances)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Month Selector for Monthly Resume
    private val _selectedMonth = MutableStateFlow(Calendar.getInstance())
    val selectedMonth = _selectedMonth.asStateFlow()

    // Month Selector for Income Tab
    private val _selectedIncomeMonth = MutableStateFlow(Calendar.getInstance())
    val selectedIncomeMonth = _selectedIncomeMonth.asStateFlow()

    fun navigateIncomeMonth(delta: Int) {
        val newCal = Calendar.getInstance().apply {
            timeInMillis = _selectedIncomeMonth.value.timeInMillis
            add(Calendar.MONTH, delta)
        }
        _selectedIncomeMonth.value = newCal
    }

    // Monthly incomes for members in the active group and active income month
    val monthlyMemberIncomes: StateFlow<List<MemberMonthlyIncome>> = combine(
        members,
        incomes,
        _selectedIncomeMonth,
        _selectedGroupId
    ) { memberList, incList, cal, grpId ->
        val currentGrpId = grpId ?: 1L
        val targetYear = cal.get(Calendar.YEAR)
        val targetMonth = cal.get(Calendar.MONTH)
        val checkCal = Calendar.getInstance()

        val groupIncomes = incList.filter { it.groupId == currentGrpId }
        val filteredIncomes = groupIncomes.filter { inc ->
            checkCal.timeInMillis = inc.date
            checkCal.get(Calendar.YEAR) == targetYear && checkCal.get(Calendar.MONTH) == targetMonth
        }

        val incomeMap = memberList.associateWith { m ->
            filteredIncomes.filter { it.memberId == m.id }.sumOf { it.amount }
        }

        val totalInc = incomeMap.values.sum()

        memberList.map { m ->
            val amt = incomeMap[m] ?: 0.0
            val pct = if (totalInc > 0) (amt / totalInc) * 100.0 else (if (memberList.isNotEmpty()) 100.0 / memberList.size else 0.0)
            MemberMonthlyIncome(
                member = m,
                totalIncome = amt,
                percentage = pct
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Group Total Income for Selected Month
    val groupMonthlyTotalIncome: StateFlow<Double> = monthlyMemberIncomes.map { list ->
        list.sumOf { it.totalIncome }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val monthlyStats: StateFlow<MonthlyResumeStats> = combine(
        expenses,
        incomes,
        _selectedMonth,
        members
    ) { expList, incList, cal, memberList ->
        val spanishLocale = Locale("es", "ES")
        val targetYear = cal.get(Calendar.YEAR)
        val targetMonth = cal.get(Calendar.MONTH)
        val rawMonth = SimpleDateFormat("MMMM yyyy", spanishLocale).format(cal.time)
        val monthTitle = rawMonth.replaceFirstChar { if (it.isLowerCase()) it.titlecase(spanishLocale) else it.toString() }

        val expCal = Calendar.getInstance()
        val filteredExpenses = expList.filter { exp ->
            expCal.timeInMillis = exp.date
            expCal.get(Calendar.YEAR) == targetYear && expCal.get(Calendar.MONTH) == targetMonth
        }

        val filteredIncomes = incList.filter { inc ->
            expCal.timeInMillis = inc.date
            expCal.get(Calendar.YEAR) == targetYear && expCal.get(Calendar.MONTH) == targetMonth
        }

        val totalGroup = filteredExpenses.sumOf { it.amount }
        val currentUserId = memberList.find { it.isCurrentUser }?.id

        var userShare = 0.0
        for (exp in filteredExpenses) {
            val splits = SplitJsonConverter.fromJson(exp.splitsJson)
            val mySplit = splits.find { it.memberId == currentUserId }
            if (mySplit != null) {
                userShare += mySplit.computedAmount
            } else if (splits.isEmpty()) {
                userShare += exp.amount / (memberList.size.coerceAtLeast(1))
            }
        }

        val totalInc = filteredIncomes.sumOf { it.amount }
        val net = totalInc - userShare
        val rate = if (totalInc > 0) (net / totalInc) * 100.0 else 0.0

        // Categories
        val catMap = mutableMapOf<ExpenseCategory, Double>()
        for (exp in filteredExpenses) {
            val cat = ExpenseCategory.fromTitle(exp.category)
            catMap[cat] = (catMap[cat] ?: 0.0) + exp.amount
        }

        val categorySpends = ExpenseCategory.entries.map { cat ->
            val amt = catMap[cat] ?: 0.0
            val pct = if (totalGroup > 0) ((amt / totalGroup) * 100.0).toFloat() else 0f
            CategorySpend(category = cat, amount = amt, percentage = pct)
        }.filter { it.amount > 0.0 }
            .sortedByDescending { it.amount }

        // Member Spends
        val memberMap = mutableMapOf<String, Double>()
        for (exp in filteredExpenses) {
            val payer = memberList.find { it.id == exp.paidByMemberId }?.name ?: "Desconocido"
            memberMap[payer] = (memberMap[payer] ?: 0.0) + exp.amount
        }
        val memberSpends = memberMap.toList().sortedByDescending { it.second }

        MonthlyResumeStats(
            monthTitle = monthTitle,
            totalGroupExpenses = totalGroup,
            userShareExpenses = userShare,
            totalIncome = totalInc,
            netSavings = net,
            savingsRate = rate,
            categorySpends = categorySpends,
            memberSpends = memberSpends,
            expenseCount = filteredExpenses.size
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        MonthlyResumeStats(
            monthTitle = "Este Mes",
            totalGroupExpenses = 0.0,
            userShareExpenses = 0.0,
            totalIncome = 0.0,
            netSavings = 0.0,
            savingsRate = 0.0,
            categorySpends = emptyList(),
            memberSpends = emptyList(),
            expenseCount = 0
        )
    )

    fun navigateMonth(delta: Int) {
        val newCal = Calendar.getInstance().apply {
            timeInMillis = _selectedMonth.value.timeInMillis
            add(Calendar.MONTH, delta)
        }
        _selectedMonth.value = newCal
    }

    fun selectGroup(group: Group) {
        _selectedGroupId.value = group.id
        _selectedGroup.value = group
    }

    fun createGroup(name: String, description: String = "", currency: String = "$") {
        viewModelScope.launch {
            val newId = repository.insertGroup(name, description, currency)
            _selectedGroupId.value = newId
        }
    }

    fun addMember(name: String, colorHex: String = "#3B82F6") {
        val currentGroupId = _selectedGroupId.value ?: return
        viewModelScope.launch {
            repository.insertMember(currentGroupId, name, colorHex)
        }
    }

    fun deleteMember(member: Member) {
        viewModelScope.launch {
            repository.deleteMember(member)
        }
    }

    fun saveExpense(
        title: String,
        amount: Double,
        category: String,
        paidByMemberId: Long,
        date: Long,
        notes: String,
        splitType: SplitType,
        splits: List<MemberSplit>
    ) {
        val currentGroupId = _selectedGroupId.value ?: return
        viewModelScope.launch {
            val expense = Expense(
                groupId = currentGroupId,
                title = title.ifBlank { category },
                amount = amount,
                category = category,
                paidByMemberId = paidByMemberId,
                date = date,
                notes = notes,
                splitType = splitType.name,
                splitsJson = SplitJsonConverter.toJson(splits)
            )
            repository.insertExpense(expense)
        }
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch {
            repository.deleteExpense(expense)
        }
    }

    fun getProportionsForDate(dateMillis: Long): Map<Long, Double> {
        val targetCal = Calendar.getInstance().apply { timeInMillis = dateMillis }
        val targetYear = targetCal.get(Calendar.YEAR)
        val targetMonth = targetCal.get(Calendar.MONTH)
        val checkCal = Calendar.getInstance()

        val memberList = members.value
        if (memberList.isEmpty()) return emptyMap()

        val currentGrpId = _selectedGroupId.value ?: 1L
        val currentGroupIncomes = incomes.value.filter { it.groupId == currentGrpId }
        var monthIncomes = currentGroupIncomes.filter { inc ->
            checkCal.timeInMillis = inc.date
            checkCal.get(Calendar.YEAR) == targetYear && checkCal.get(Calendar.MONTH) == targetMonth
        }

        if (monthIncomes.isEmpty() && currentGroupIncomes.isNotEmpty()) {
            monthIncomes = currentGroupIncomes
        }

        val memberIncomeMap = memberList.associateWith { m ->
            monthIncomes.filter { it.memberId == m.id }.sumOf { it.amount }
        }
        val totalIncome = memberIncomeMap.values.sum()

        return if (totalIncome > 0.0) {
            memberList.associate { it.id to ((memberIncomeMap[it] ?: 0.0) / totalIncome) * 100.0 }
        } else {
            val equalPct = 100.0 / memberList.size
            memberList.associate { it.id to equalPct }
        }
    }

    fun getMemberMonthlyEarningsForDate(dateMillis: Long): Map<Long, Double> {
        val targetCal = Calendar.getInstance().apply { timeInMillis = dateMillis }
        val targetYear = targetCal.get(Calendar.YEAR)
        val targetMonth = targetCal.get(Calendar.MONTH)
        val checkCal = Calendar.getInstance()

        val memberList = members.value
        val currentGrpId = _selectedGroupId.value ?: 1L
        val currentGroupIncomes = incomes.value.filter { it.groupId == currentGrpId }
        var monthIncomes = currentGroupIncomes.filter { inc ->
            checkCal.timeInMillis = inc.date
            checkCal.get(Calendar.YEAR) == targetYear && checkCal.get(Calendar.MONTH) == targetMonth
        }
        if (monthIncomes.isEmpty() && currentGroupIncomes.isNotEmpty()) {
            monthIncomes = currentGroupIncomes
        }

        return memberList.associate { m ->
            m.id to monthIncomes.filter { it.memberId == m.id }.sumOf { it.amount }
        }
    }

    fun saveIncome(
        title: String,
        amount: Double,
        source: String,
        date: Long,
        notes: String,
        memberId: Long? = null,
        memberName: String = ""
    ) {
        val currentGroupId = _selectedGroupId.value ?: 1L
        viewModelScope.launch {
            val income = Income(
                groupId = currentGroupId,
                memberId = memberId,
                memberName = memberName,
                title = title.ifBlank { "Ingreso de $memberName" },
                amount = amount,
                source = source,
                date = date,
                notes = notes
            )
            repository.insertIncome(income)
        }
    }

    fun setMemberMonthlyIncome(memberId: Long, memberName: String, amount: Double, monthCal: Calendar) {
        val currentGroupId = _selectedGroupId.value ?: 1L
        viewModelScope.launch {
            val targetYear = monthCal.get(Calendar.YEAR)
            val targetMonth = monthCal.get(Calendar.MONTH)
            val checkCal = Calendar.getInstance()

            val existing = incomes.value.find { inc ->
                inc.groupId == currentGroupId && inc.memberId == memberId && checkCal.apply { timeInMillis = inc.date }.let {
                    it.get(Calendar.YEAR) == targetYear && it.get(Calendar.MONTH) == targetMonth
                }
            }

            if (existing != null) {
                repository.updateIncome(existing.copy(amount = amount))
            } else {
                val income = Income(
                    groupId = currentGroupId,
                    memberId = memberId,
                    memberName = memberName,
                    title = "Ganancia Mensual",
                    amount = amount,
                    source = "Sueldo",
                    date = monthCal.timeInMillis,
                    notes = "Ganancia mensual para cálculo de gastos proporcionales"
                )
                repository.insertIncome(income)
            }
        }
    }

    fun setGroupMonthlyEarnings(earningsMap: Map<Long, Double>, monthCal: Calendar) {
        val currentGroupId = _selectedGroupId.value ?: 1L
        val memberList = members.value
        viewModelScope.launch {
            val targetYear = monthCal.get(Calendar.YEAR)
            val targetMonth = monthCal.get(Calendar.MONTH)
            val checkCal = Calendar.getInstance()

            for ((memberId, amount) in earningsMap) {
                val member = memberList.find { it.id == memberId }
                val memberName = member?.name ?: "Miembro"

                val existing = incomes.value.find { inc ->
                    inc.groupId == currentGroupId && inc.memberId == memberId && checkCal.apply { timeInMillis = inc.date }.let {
                        it.get(Calendar.YEAR) == targetYear && it.get(Calendar.MONTH) == targetMonth
                    }
                }

                if (existing != null) {
                    repository.updateIncome(existing.copy(amount = amount))
                } else {
                    val income = Income(
                        groupId = currentGroupId,
                        memberId = memberId,
                        memberName = memberName,
                        title = "Ganancia Mensual",
                        amount = amount,
                        source = "Sueldo",
                        date = monthCal.timeInMillis,
                        notes = "Ganancia mensual para cálculo de gastos proporcionales"
                    )
                    repository.insertIncome(income)
                }
            }
        }
    }

    fun deleteIncome(income: Income) {
        viewModelScope.launch {
            repository.deleteIncome(income)
        }
    }

    fun saveInvestment(
        name: String,
        symbol: String,
        assetType: String,
        invested: Double,
        valuation: Double,
        quantity: Double,
        notes: String
    ) {
        viewModelScope.launch {
            val investment = Investment(
                name = name,
                symbol = symbol.uppercase(),
                assetType = assetType,
                investedAmount = invested,
                currentValuation = valuation,
                quantity = quantity,
                notes = notes
            )
            repository.insertInvestment(investment)
        }
    }

    fun updateInvestment(investment: Investment) {
        viewModelScope.launch {
            repository.updateInvestment(investment)
        }
    }

    fun deleteInvestment(investment: Investment) {
        viewModelScope.launch {
            repository.deleteInvestment(investment)
        }
    }

    fun syncAll(onComplete: (Int) -> Unit) {
        viewModelScope.launch {
            val count = repository.syncAll()
            onComplete(count)
        }
    }

    fun parseVoice(text: String): ParsedVoiceExpense {
        val names = members.value.map { it.name }
        return ExpenseVoiceParser.parse(text, names)
    }
}
