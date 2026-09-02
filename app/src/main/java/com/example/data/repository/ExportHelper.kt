package com.example.data.repository

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.model.Expense
import com.example.data.model.Income
import com.example.data.model.Investment
import com.example.data.model.Member
import com.example.data.model.SplitJsonConverter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportHelper {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    fun generateExpensesCsv(expenses: List<Expense>, membersMap: Map<Long, String>, groupName: String): String {
        val sb = StringBuilder()
        sb.append("Fecha,Grupo,Título,Categoría,Monto,Pagado Por,Tipo de División,Distribuciones,Notas,Estado de Sincronización\n")

        for (exp in expenses) {
            val dateStr = dateFormat.format(Date(exp.date))
            val payerName = membersMap[exp.paidByMemberId] ?: "Desconocido"
            val splits = SplitJsonConverter.fromJson(exp.splitsJson)
            val allocationsStr = splits.joinToString("; ") { "${it.memberName}: $${String.format(Locale.US, "%.2f", it.computedAmount)}" }

            sb.append("\"$dateStr\",")
            sb.append("\"${escapeCsv(groupName)}\",")
            sb.append("\"${escapeCsv(exp.title)}\",")
            sb.append("\"${escapeCsv(exp.category)}\",")
            sb.append("${String.format(Locale.US, "%.2f", exp.amount)},")
            sb.append("\"${escapeCsv(payerName)}\",")
            sb.append("\"${exp.splitType}\",")
            sb.append("\"${escapeCsv(allocationsStr)}\",")
            sb.append("\"${escapeCsv(exp.notes)}\",")
            sb.append("\"${exp.syncStatus}\"\n")
        }
        return sb.toString()
    }

    fun generateIncomeCsv(incomes: List<Income>): String {
        val sb = StringBuilder()
        sb.append("Fecha,Título,Fuente,Monto,Notas,Estado de Sincronización\n")

        for (inc in incomes) {
            val dateStr = dateFormat.format(Date(inc.date))
            sb.append("\"$dateStr\",")
            sb.append("\"${escapeCsv(inc.title)}\",")
            sb.append("\"${escapeCsv(inc.source)}\",")
            sb.append("${String.format(Locale.US, "%.2f", inc.amount)},")
            sb.append("\"${escapeCsv(inc.notes)}\",")
            sb.append("\"${inc.syncStatus}\"\n")
        }
        return sb.toString()
    }

    fun generateInvestmentsCsv(investments: List<Investment>): String {
        val sb = StringBuilder()
        sb.append("Nombre del Activo,Símbolo,Tipo de Activo,Cantidad,Monto Invertido,Valoración Actual,Ganancia/Pérdida,Rendimiento %,Notas,Estado de Sincronización\n")

        for (inv in investments) {
            sb.append("\"${escapeCsv(inv.name)}\",")
            sb.append("\"${escapeCsv(inv.symbol)}\",")
            sb.append("\"${escapeCsv(inv.assetType)}\",")
            sb.append("${inv.quantity},")
            sb.append("${String.format(Locale.US, "%.2f", inv.investedAmount)},")
            sb.append("${String.format(Locale.US, "%.2f", inv.currentValuation)},")
            sb.append("${String.format(Locale.US, "%.2f", inv.profitLoss)},")
            sb.append("${String.format(Locale.US, "%.2f", inv.returnPercentage)}%,")
            sb.append("\"${escapeCsv(inv.notes)}\",")
            sb.append("\"${inv.syncStatus}\"\n")
        }
        return sb.toString()
    }

    fun generateCompleteReportCsv(
        expenses: List<Expense>,
        incomes: List<Income>,
        investments: List<Investment>,
        membersMap: Map<Long, String>,
        groupName: String,
        monthLabel: String
    ): String {
        val sb = StringBuilder()
        sb.append("=== REPORTE COMPLETO CASA RL. - $monthLabel ===\n\n")

        val totalExpenses = expenses.sumOf { it.amount }
        val totalIncome = incomes.sumOf { it.amount }
        val totalInvested = investments.sumOf { it.investedAmount }
        val totalPortfolioValue = investments.sumOf { it.currentValuation }

        sb.append("MÉTRICAS DE RESUMEN\n")
        sb.append("Total Gastos del Grupo,$${String.format(Locale.US, "%.2f", totalExpenses)}\n")
        sb.append("Total Ingresos Registrados,$${String.format(Locale.US, "%.2f", totalIncome)}\n")
        sb.append("Flujo Neto (Ingresos - Gastos),$${String.format(Locale.US, "%.2f", totalIncome - totalExpenses)}\n")
        sb.append("Portafolio Total de Inversiones,$${String.format(Locale.US, "%.2f", totalPortfolioValue)}\n")
        sb.append("Ganancias Totales en Inversión,$${String.format(Locale.US, "%.2f", totalPortfolioValue - totalInvested)}\n\n")

        sb.append("--- GASTOS ---\n")
        sb.append(generateExpensesCsv(expenses, membersMap, groupName))
        sb.append("\n")

        sb.append("--- INGRESOS ---\n")
        sb.append(generateIncomeCsv(incomes))
        sb.append("\n")

        sb.append("--- PORTAFOLIO DE INVERSIONES ---\n")
        sb.append(generateInvestmentsCsv(investments))

        return sb.toString()
    }

    fun shareCsv(context: Context, filename: String, csvContent: String, title: String = "Exportar CSV a Google Sheets o Drive") {
        try {
            val cachePath = File(context.cacheDir, "exports")
            cachePath.mkdirs()
            val file = File(cachePath, "$filename.csv")
            val writer = FileWriter(file)
            writer.write(csvContent)
            writer.close()

            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, "$filename - Exportación casa rl.")
                putExtra(Intent.EXTRA_TEXT, "Datos exportados desde casa rl. Abrir con Google Sheets, Excel o Google Drive.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(shareIntent, title))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error de exportación: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    fun copyToClipboard(context: Context, label: String, content: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, content)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "¡CSV copiado al portapapeles! Listo para pegar en Google Sheets.", Toast.LENGTH_SHORT).show()
    }

    private fun escapeCsv(value: String): String {
        return value.replace("\"", "\"\"").replace("\n", " ")
    }
}
