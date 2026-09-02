package com.example.voice

import com.example.data.model.ExpenseCategory
import com.example.data.model.SplitType
import java.util.regex.Pattern

data class ParsedVoiceExpense(
    val title: String,
    val amount: Double,
    val category: ExpenseCategory,
    val splitType: SplitType,
    val payerName: String? = null,
    val rawTranscript: String,
    val customAllocations: Map<String, Double> = emptyMap()
)

object ExpenseVoiceParser {

    fun parse(text: String, knownMemberNames: List<String> = emptyList()): ParsedVoiceExpense {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return ParsedVoiceExpense(
                title = "New Expense",
                amount = 0.0,
                category = ExpenseCategory.GENERAL,
                splitType = SplitType.EQUAL,
                rawTranscript = text
            )
        }

        // 1. Extract Amount: e.g. "$45.50", "45.50", "45 dollars", "45 pesos", "45 euros"
        var extractedAmount = 0.0
        val amountPattern = Pattern.compile("(?:\\$|USD\\s*|EUR\\s*)?(\\d+(?:[.,]\\d{1,2})?)(?:\\s*(?:dollars?|bucks?|usd|€|euros?|pesos?))?", Pattern.CASE_INSENSITIVE)
        val amountMatcher = amountPattern.matcher(trimmed)
        var amountMatchedString = ""
        // Search for the most plausible amount (often the first or after currency keyword)
        while (amountMatcher.find()) {
            val numStr = amountMatcher.group(1)?.replace(",", ".")
            val parsed = numStr?.toDoubleOrNull() ?: 0.0
            if (parsed > 0.0) {
                extractedAmount = parsed
                amountMatchedString = amountMatcher.group(0) ?: ""
                break
            }
        }

        // 2. Extract Category
        val detectedCategory = ExpenseCategory.fromKeywords(trimmed)

        // 3. Extract Split Type
        val lower = trimmed.lowercase()
        val detectedSplitType = when {
            lower.contains("proporcional") || lower.contains("ingreso") || lower.contains("ganancia") || lower.contains("sueldo") || lower.contains("salario") -> SplitType.PROPORTIONAL_INCOME
            lower.contains("%") || lower.contains("percent") || lower.contains("porcent") || lower.contains("por ciento") -> SplitType.PERCENTAGE
            lower.contains("part") || lower.contains("share") || lower.contains("cuota") -> SplitType.PARTS
            lower.contains("exact") || lower.contains("custom amount") || lower.contains("monto exacto") -> SplitType.EXACT_AMOUNTS
            else -> SplitType.EQUAL
        }

        // 4. Check Payer if mentioned: e.g. "paid by Alice", "Alice paid", "pagado por Alice", "pagó Alice", "pago Alice"
        var detectedPayer: String? = null
        for (name in knownMemberNames) {
            val nameLower = name.lowercase()
            if (lower.contains("paid by $nameLower") || lower.contains("by $nameLower") || lower.contains("$nameLower paid") ||
                lower.contains("pagado por $nameLower") || lower.contains("pago $nameLower") || lower.contains("pagó $nameLower") ||
                lower.contains("por $nameLower")) {
                detectedPayer = name
                break
            }
        }

        // 5. Check custom allocations (e.g. "Alice 60% Bob 40%" or "2 partes Bob 1 parte Alice")
        val allocations = mutableMapOf<String, Double>()
        for (name in knownMemberNames) {
            val nameLower = name.lowercase()
            // Check percentage pattern: "Alice 60%" or "60% Alice"
            val pctPattern1 = Pattern.compile("$nameLower\\s*(\\d+)%", Pattern.CASE_INSENSITIVE)
            val pctMatcher1 = pctPattern1.matcher(trimmed)
            if (pctMatcher1.find()) {
                allocations[name] = pctMatcher1.group(1)?.toDoubleOrNull() ?: 0.0
            } else {
                val pctPattern2 = Pattern.compile("(\\d+)%\\s*$nameLower", Pattern.CASE_INSENSITIVE)
                val pctMatcher2 = pctPattern2.matcher(trimmed)
                if (pctMatcher2.find()) {
                    allocations[name] = pctMatcher2.group(1)?.toDoubleOrNull() ?: 0.0
                }
            }

            // Check parts pattern: "2 parts Bob" or "Bob 2 parts" or "2 partes Bob"
            val partPattern1 = Pattern.compile("(\\d+)\\s*(?:parts?|shares?|partes?|cuotas?)\\s*$nameLower", Pattern.CASE_INSENSITIVE)
            val partMatcher1 = partPattern1.matcher(trimmed)
            if (partMatcher1.find()) {
                allocations[name] = partMatcher1.group(1)?.toDoubleOrNull() ?: 1.0
            } else {
                val partPattern2 = Pattern.compile("$nameLower\\s*(\\d+)\\s*(?:parts?|shares?|partes?|cuotas?)", Pattern.CASE_INSENSITIVE)
                val partMatcher2 = partPattern2.matcher(trimmed)
                if (partMatcher2.find()) {
                    allocations[name] = partMatcher2.group(1)?.toDoubleOrNull() ?: 1.0
                }
            }
        }

        // 6. Clean Title
        var title = trimmed
        // Remove amount string
        if (amountMatchedString.isNotEmpty()) {
            title = title.replace(amountMatchedString, "", ignoreCase = true)
        }
        // Remove split keywords
        title = title.replace(Regex("(?i)\\b(split|equally|even|evenly|50\\s*50|percentages?|parts?|shares?|partes?|iguales|porcentajes?|cuotas?)\\b"), "")
        // Remove "paid by [name]" / "pagado por [name]"
        title = title.replace(Regex("(?i)\\b(paid by|by|pagado por|pagó|pago|por)\\s+\\w+\\b"), "")
        // Remove category keyword if explicitly preceded by "category" or "categoría"
        title = title.replace(Regex("(?i)\\b(category|categoría|categoria)\\s+\\w+\\b"), "")
        // Remove percentage & parts clauses from title
        title = title.replace(Regex("\\d+%(?:\\s*\\w+)?"), "")
        title = title.replace(Regex("\\d+\\s*(?:parts?|shares?|partes?|cuotas?)(?:\\s*\\w+)?"), "")
        // Clean up punctuation and whitespace
        title = title.replace(Regex("[$,:]"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

        if (title.isBlank() || title.length < 2) {
            title = if (detectedCategory != ExpenseCategory.GENERAL) {
                detectedCategory.title
            } else {
                "Gasto de grupo"
            }
        } else {
            // Capitalize first letter
            title = title.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }

        return ParsedVoiceExpense(
            title = title,
            amount = extractedAmount,
            category = detectedCategory,
            splitType = detectedSplitType,
            payerName = detectedPayer,
            rawTranscript = text,
            customAllocations = allocations
        )
    }
}
