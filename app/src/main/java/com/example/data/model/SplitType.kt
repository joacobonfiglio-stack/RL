package com.example.data.model

enum class SplitType(val label: String, val description: String) {
    EQUAL("Partes Iguales", "Dividir en partes iguales entre todos los miembros"),
    PROPORTIONAL_INCOME("Proporcional a Ganancias", "Dividir proporcional a los ingresos mensuales de cada miembro"),
    PERCENTAGE("Porcentajes", "Dividir por porcentaje asignado a cada miembro"),
    PARTS("Partes / Cuotas", "Dividir por porciones o cuotas (ej. 2 partes vs 1 parte)"),
    EXACT_AMOUNTS("Montos Exactos", "Especificar el monto exacto para cada miembro")
}
