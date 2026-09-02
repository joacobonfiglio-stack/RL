package com.example.data.repository

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
import com.example.data.model.SyncStatus
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.min

data class MemberBalance(
    val memberId: Long,
    val memberName: String,
    val avatarColorHex: String,
    val isCurrentUser: Boolean,
    val totalPaid: Double,
    val totalOwed: Double,
    val netBalance: Double // > 0 get back, < 0 owes
)

data class DebtSettlement(
    val fromMemberId: Long,
    val fromMemberName: String,
    val toMemberId: Long,
    val toMemberName: String,
    val amount: Double
)

class ExpenseRepository(private val database: AppDatabase) {

    private val groupDao = database.groupDao()
    private val memberDao = database.memberDao()
    private val expenseDao = database.expenseDao()
    private val incomeDao = database.incomeDao()
    private val investmentDao = database.investmentDao()

    val allGroups: Flow<List<Group>> = groupDao.getAllGroups()
    val allIncomes: Flow<List<Income>> = incomeDao.getAllIncomes()
    val allInvestments: Flow<List<Investment>> = investmentDao.getAllInvestments()
    val allExpenses: Flow<List<Expense>> = expenseDao.getAllExpenses()
    val pendingSyncCount: Flow<Int> = expenseDao.getPendingSyncCount()

    fun getMembersForGroup(groupId: Long): Flow<List<Member>> = memberDao.getMembersForGroup(groupId)
    fun getExpensesForGroup(groupId: Long): Flow<List<Expense>> = expenseDao.getExpensesForGroup(groupId)
    fun getIncomesForGroup(groupId: Long): Flow<List<Income>> = incomeDao.getIncomesForGroup(groupId)

    suspend fun insertGroup(name: String, description: String = "", currency: String = "$"): Long {
        val group = Group(name = name, description = description, currencySymbol = currency)
        val id = groupDao.insert(group)
        // Add current user as default member
        memberDao.insert(Member(groupId = id, name = "Tú", avatarColorHex = "#10B981", isCurrentUser = true))
        return id
    }

    suspend fun insertMember(groupId: Long, name: String, colorHex: String = "#3B82F6"): Long {
        return memberDao.insert(Member(groupId = groupId, name = name, avatarColorHex = colorHex))
    }

    suspend fun deleteMember(member: Member) = memberDao.delete(member)

    suspend fun insertExpense(expense: Expense): Long {
        return expenseDao.insert(expense.copy(syncStatus = SyncStatus.LOCAL_ONLY.name, updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateExpense(expense: Expense) {
        expenseDao.update(expense.copy(syncStatus = SyncStatus.LOCAL_ONLY.name, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteExpense(expense: Expense) = expenseDao.delete(expense)

    suspend fun insertIncome(income: Income): Long {
        return incomeDao.insert(income.copy(syncStatus = SyncStatus.LOCAL_ONLY.name))
    }

    suspend fun updateIncome(income: Income) {
        incomeDao.update(income.copy(syncStatus = SyncStatus.LOCAL_ONLY.name))
    }

    suspend fun deleteIncome(income: Income) = incomeDao.delete(income)

    suspend fun insertInvestment(investment: Investment): Long {
        return investmentDao.insert(investment.copy(syncStatus = SyncStatus.LOCAL_ONLY.name, updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateInvestment(investment: Investment) {
        investmentDao.update(investment.copy(syncStatus = SyncStatus.LOCAL_ONLY.name, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteInvestment(investment: Investment) = investmentDao.delete(investment)

    suspend fun syncAll(): Int {
        // Sync local records: updates local & pending to synced
        expenseDao.markAllAsSynced(SyncStatus.LOCAL_ONLY.name, SyncStatus.SYNCED.name)
        expenseDao.markAllAsSynced(SyncStatus.PENDING_SYNC.name, SyncStatus.SYNCED.name)
        incomeDao.markAllAsSynced(SyncStatus.LOCAL_ONLY.name, SyncStatus.SYNCED.name)
        incomeDao.markAllAsSynced(SyncStatus.PENDING_SYNC.name, SyncStatus.SYNCED.name)
        investmentDao.markAllAsSynced(SyncStatus.LOCAL_ONLY.name, SyncStatus.SYNCED.name)
        investmentDao.markAllAsSynced(SyncStatus.PENDING_SYNC.name, SyncStatus.SYNCED.name)
        return 1
    }

    fun calculateBalances(members: List<Member>, expenses: List<Expense>): List<MemberBalance> {
        val paidMap = mutableMapOf<Long, Double>()
        val owedMap = mutableMapOf<Long, Double>()

        for (m in members) {
            paidMap[m.id] = 0.0
            owedMap[m.id] = 0.0
        }

        for (exp in expenses) {
            paidMap[exp.paidByMemberId] = (paidMap[exp.paidByMemberId] ?: 0.0) + exp.amount
            val splits = SplitJsonConverter.fromJson(exp.splitsJson)
            for (split in splits) {
                owedMap[split.memberId] = (owedMap[split.memberId] ?: 0.0) + split.computedAmount
            }
        }

        return members.map { member ->
            val paid = paidMap[member.id] ?: 0.0
            val owed = owedMap[member.id] ?: 0.0
            MemberBalance(
                memberId = member.id,
                memberName = member.name,
                avatarColorHex = member.avatarColorHex,
                isCurrentUser = member.isCurrentUser,
                totalPaid = paid,
                totalOwed = owed,
                netBalance = paid - owed
            )
        }
    }

    fun calculateSettlements(balances: List<MemberBalance>): List<DebtSettlement> {
        val debtors = balances.filter { it.netBalance < -0.01 }
            .map { it.memberId to it.memberName to -it.netBalance }
            .map { Triple(it.first.first, it.first.second, it.second) }
            .toMutableList()

        val creditors = balances.filter { it.netBalance > 0.01 }
            .map { it.memberId to it.memberName to it.netBalance }
            .map { Triple(it.first.first, it.first.second, it.second) }
            .toMutableList()

        val settlements = mutableListOf<DebtSettlement>()

        var debtorIdx = 0
        var creditorIdx = 0

        while (debtorIdx < debtors.size && creditorIdx < creditors.size) {
            val (debtorId, debtorName, debtAmt) = debtors[debtorIdx]
            val (creditorId, creditorName, credAmt) = creditors[creditorIdx]

            val settleAmt = min(debtAmt, credAmt)
            if (settleAmt > 0.01) {
                settlements.add(
                    DebtSettlement(
                        fromMemberId = debtorId,
                        fromMemberName = debtorName,
                        toMemberId = creditorId,
                        toMemberName = creditorName,
                        amount = settleAmt
                    )
                )
            }

            debtors[debtorIdx] = Triple(debtorId, debtorName, debtAmt - settleAmt)
            creditors[creditorIdx] = Triple(creditorId, creditorName, credAmt - settleAmt)

            if (debtors[debtorIdx].third < 0.01) debtorIdx++
            if (creditors[creditorIdx].third < 0.01) creditorIdx++
        }

        return settlements
    }

    suspend fun checkAndSeedInitialData() {
        if (groupDao.getGroupCount() > 0) return

        val groupId = groupDao.insert(
            Group(
                name = "casa rl.",
                description = "Gastos diarios compartidos del hogar",
                currencySymbol = "$"
            )
        )

        val memberYou = Member(groupId = groupId, name = "Tú", avatarColorHex = "#10B981", isCurrentUser = true)
        val memberAlex = Member(groupId = groupId, name = "Alex", avatarColorHex = "#3B82F6")
        val memberSam = Member(groupId = groupId, name = "Sam", avatarColorHex = "#F59E0B")
        val memberTaylor = Member(groupId = groupId, name = "Taylor", avatarColorHex = "#EC4899")

        val youId = memberDao.insert(memberYou)
        val alexId = memberDao.insert(memberAlex)
        val samId = memberDao.insert(memberSam)
        val taylorId = memberDao.insert(memberTaylor)

        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()

        // 1. Today - Groceries (Equal split)
        cal.timeInMillis = now
        val todayMillis = cal.timeInMillis
        val grocSplits = listOf(
            MemberSplit(youId, "Tú", 1.0, 32.50),
            MemberSplit(alexId, "Alex", 1.0, 32.50),
            MemberSplit(samId, "Sam", 1.0, 32.50),
            MemberSplit(taylorId, "Taylor", 1.0, 32.50)
        )
        expenseDao.insert(
            Expense(
                groupId = groupId,
                title = "Compra en Supermercado",
                amount = 130.00,
                category = ExpenseCategory.GROCERIES.title,
                paidByMemberId = youId,
                date = todayMillis,
                notes = "Compras semanales de alimentos, frutas y café",
                splitType = SplitType.EQUAL.name,
                splitsJson = SplitJsonConverter.toJson(grocSplits),
                syncStatus = SyncStatus.SYNCED.name
            )
        )

        // 2. Today - Dinner & Drinks (Percentages: You 40%, Alex 30%, Sam 30%)
        cal.add(Calendar.HOUR_OF_DAY, -3)
        val dinnerSplits = listOf(
            MemberSplit(youId, "Tú", 40.0, 38.00),
            MemberSplit(alexId, "Alex", 30.0, 28.50),
            MemberSplit(samId, "Sam", 30.0, 28.50)
        )
        expenseDao.insert(
            Expense(
                groupId = groupId,
                title = "Cena de Pizza y Cervezas",
                amount = 95.00,
                category = ExpenseCategory.FOOD.title,
                paidByMemberId = alexId,
                date = cal.timeInMillis,
                notes = "Pizza artesanal y bebidas para el grupo",
                splitType = SplitType.PERCENTAGE.name,
                splitsJson = SplitJsonConverter.toJson(dinnerSplits),
                syncStatus = SyncStatus.SYNCED.name
            )
        )

        // 3. Yesterday - High-Speed Fiber Internet (Equal split among all 4)
        cal.timeInMillis = now
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayMillis = cal.timeInMillis
        val internetSplits = listOf(
            MemberSplit(youId, "Tú", 1.0, 20.00),
            MemberSplit(alexId, "Alex", 1.0, 20.00),
            MemberSplit(samId, "Sam", 1.0, 20.00),
            MemberSplit(taylorId, "Taylor", 1.0, 20.00)
        )
        expenseDao.insert(
            Expense(
                groupId = groupId,
                title = "Internet Fibra Óptica",
                amount = 80.00,
                category = ExpenseCategory.UTILITIES.title,
                paidByMemberId = samId,
                date = yesterdayMillis,
                notes = "Factura mensual de fibra óptica de la casa",
                splitType = SplitType.EQUAL.name,
                splitsJson = SplitJsonConverter.toJson(internetSplits),
                syncStatus = SyncStatus.SYNCED.name
            )
        )

        // 4. 2 Days Ago - Weekend Uber XL (Parts split: Taylor 2 parts, Alex 1 part, You 1 part)
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val uberSplits = listOf(
            MemberSplit(taylorId, "Taylor", 2.0, 24.00),
            MemberSplit(alexId, "Alex", 1.0, 12.00),
            MemberSplit(youId, "Tú", 1.0, 12.00)
        )
        expenseDao.insert(
            Expense(
                groupId = groupId,
                title = "Viaje en Uber Grupo",
                amount = 48.00,
                category = ExpenseCategory.TRANSPORT.title,
                paidByMemberId = taylorId,
                date = cal.timeInMillis,
                notes = "Traslado en auto compartido de regreso",
                splitType = SplitType.PARTS.name,
                splitsJson = SplitJsonConverter.toJson(uberSplits),
                syncStatus = SyncStatus.SYNCED.name
            )
        )

        // 5. 4 Days Ago - Electricity & Gas (Equal split)
        cal.add(Calendar.DAY_OF_YEAR, -2)
        val electricSplits = listOf(
            MemberSplit(youId, "Tú", 1.0, 45.00),
            MemberSplit(alexId, "Alex", 1.0, 45.00),
            MemberSplit(samId, "Sam", 1.0, 45.00),
            MemberSplit(taylorId, "Taylor", 1.0, 45.00)
        )
        expenseDao.insert(
            Expense(
                groupId = groupId,
                title = "Factura de Luz y Electricidad",
                amount = 180.00,
                category = ExpenseCategory.UTILITIES.title,
                paidByMemberId = youId,
                date = cal.timeInMillis,
                notes = "Consumo mensual de electricidad de la casa",
                splitType = SplitType.EQUAL.name,
                splitsJson = SplitJsonConverter.toJson(electricSplits),
                syncStatus = SyncStatus.SYNCED.name
            )
        )

        // Seed Sample Incomes per member for proportional calculations
        incomeDao.insert(
            Income(
                groupId = groupId,
                memberId = youId,
                memberName = "Tú",
                title = "Sueldo Mensual",
                amount = 4500.00,
                source = "Sueldo",
                date = now - (86400000L * 2),
                notes = "Depósito nómina fija mensual",
                syncStatus = SyncStatus.SYNCED.name
            )
        )
        incomeDao.insert(
            Income(
                groupId = groupId,
                memberId = alexId,
                memberName = "Alex",
                title = "Sueldo Ingeniería",
                amount = 3000.00,
                source = "Sueldo",
                date = now - (86400000L * 3),
                notes = "Salario desarrollo software",
                syncStatus = SyncStatus.SYNCED.name
            )
        )
        incomeDao.insert(
            Income(
                groupId = groupId,
                memberId = samId,
                memberName = "Sam",
                title = "Ingresos Diseño UX",
                amount = 2500.00,
                source = "Freelance",
                date = now - (86400000L * 5),
                notes = "Honorarios diseño de interfaces",
                syncStatus = SyncStatus.SYNCED.name
            )
        )
        incomeDao.insert(
            Income(
                groupId = groupId,
                memberId = taylorId,
                memberName = "Taylor",
                title = "Comisiones de Ventas",
                amount = 2000.00,
                source = "Negocio",
                date = now - (86400000L * 8),
                notes = "Ventas mensuales",
                syncStatus = SyncStatus.SYNCED.name
            )
        )

        // Seed Sample Investments
        investmentDao.insert(
            Investment(
                name = "Vanguard S&P 500 ETF",
                symbol = "VOO",
                assetType = "Acciones y ETFs",
                investedAmount = 5200.00,
                currentValuation = 6480.00,
                quantity = 12.5,
                notes = "Fondo indexado diversificado",
                syncStatus = SyncStatus.SYNCED.name
            )
        )
        investmentDao.insert(
            Investment(
                name = "Apple Inc.",
                symbol = "AAPL",
                assetType = "Acciones y ETFs",
                investedAmount = 2100.00,
                currentValuation = 2890.00,
                quantity = 13.0,
                notes = "Posición a largo plazo en tecnología",
                syncStatus = SyncStatus.SYNCED.name
            )
        )
        investmentDao.insert(
            Investment(
                name = "Bitcoin",
                symbol = "BTC",
                assetType = "Criptomonedas",
                investedAmount = 1800.00,
                currentValuation = 2350.00,
                quantity = 0.038,
                notes = "Billetera fría segura",
                syncStatus = SyncStatus.SYNCED.name
            )
        )
        investmentDao.insert(
            Investment(
                name = "Realty Income REIT",
                symbol = "O",
                assetType = "Bienes Raíces",
                investedAmount = 1500.00,
                currentValuation = 1620.00,
                quantity = 30.0,
                notes = "Fideicomiso de dividendos mensuales",
                syncStatus = SyncStatus.SYNCED.name
            )
        )
    }
}
