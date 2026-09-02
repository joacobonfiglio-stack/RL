package com.example.data.model

enum class ExpenseCategory(
    val title: String,
    val colorHex: String,
    val keywords: List<String>
) {
    FOOD("Comida y Restaurantes", "#EF4444", listOf("food", "comida", "almuerzo", "cena", "desayuno", "restaurante", "cafe", "café", "pizza", "hamburguesa", "burger", "drinks", "bebidas", "bar", "snack", "tacos", "taco", "sushi", "tragos")),
    GROCERIES("Supermercado", "#10B981", listOf("groceries", "super", "supermercado", "mercado", "almacen", "almacén", "leche", "verduras", "frutas", "pan", "huevos", "carne", "produce", "provisiones")),
    HOUSING("Vivienda y Alquiler", "#8B5CF6", listOf("housing", "rent", "alquiler", "renta", "hipoteca", "casa", "departamento", "piso", "habitacion", "mantenimiento", "reparacion", "expensas")),
    UTILITIES("Servicios y Facturas", "#F59E0B", listOf("utilities", "utility", "servicios", "luz", "electricidad", "agua", "gas", "internet", "wifi", "telefono", "teléfono", "factura", "energia")),
    TRANSPORT("Transporte", "#3B82F6", listOf("transport", "transporte", "uber", "cabify", "didi", "taxi", "gasolina", "nafta", "combustible", "subte", "metro", "colectivo", "bus", "vuelo", "pasaje", "estacionamiento", "peaje", "tren", "auto", "coche")),
    ENTERTAINMENT("Entretenimiento", "#EC4899", listOf("entertainment", "entretenimiento", "cine", "pelicula", "película", "concierto", "recital", "juego", "netflix", "spotify", "fiesta", "boliche", "club", "evento", "teatro", "museo")),
    SHOPPING("Compras", "#6366F1", listOf("shopping", "compras", "ropa", "zapatillas", "zapatos", "electronica", "electrónica", "tienda", "muebles", "amazon", "mercado libre")),
    HEALTH("Salud y Bienestar", "#14B8A6", listOf("health", "salud", "gimnasio", "gym", "medico", "médico", "farmacia", "medicamento", "remedios", "dentista", "clinica", "clínica", "hospital", "vitaminas")),
    GENERAL("Otros", "#64748B", listOf("other", "otros", "otro", "misc", "general", "varios", "gasto"));

    companion object {
        fun fromKeywords(text: String): ExpenseCategory {
            val lower = text.lowercase()
            for (cat in entries) {
                if (cat.keywords.any { lower.contains(it) }) {
                    return cat
                }
            }
            return GENERAL
        }

        fun fromTitle(title: String): ExpenseCategory {
            return entries.find { 
                it.title.equals(title, ignoreCase = true) || 
                it.name.equals(title, ignoreCase = true) ||
                (it == FOOD && (title.contains("Food", ignoreCase = true) || title.contains("Comida", ignoreCase = true))) ||
                (it == GROCERIES && (title.contains("Groceries", ignoreCase = true) || title.contains("Super", ignoreCase = true))) ||
                (it == HOUSING && (title.contains("Housing", ignoreCase = true) || title.contains("Vivienda", ignoreCase = true))) ||
                (it == UTILITIES && (title.contains("Utilities", ignoreCase = true) || title.contains("Servicios", ignoreCase = true))) ||
                (it == TRANSPORT && (title.contains("Transport", ignoreCase = true) || title.contains("Transporte", ignoreCase = true))) ||
                (it == ENTERTAINMENT && (title.contains("Entertainment", ignoreCase = true) || title.contains("Entretenimiento", ignoreCase = true))) ||
                (it == SHOPPING && (title.contains("Shopping", ignoreCase = true) || title.contains("Compras", ignoreCase = true))) ||
                (it == HEALTH && (title.contains("Health", ignoreCase = true) || title.contains("Salud", ignoreCase = true)))
            } ?: GENERAL
        }
    }
}
