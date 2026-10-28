package com.cambiopro.app.domain

object HistoryLogic {
    const val MAX = 100

    /** Insere no topo, remove repetição idêntica consecutiva (mesmo par e valor) e limita a [max] itens. */
    fun add(current: List<ConversionRecord>, record: ConversionRecord, max: Int = MAX): List<ConversionRecord> {
        val head = current.firstOrNull()
        val rest = if (head != null &&
            head.fromKey == record.fromKey && head.toKey == record.toKey && head.amount == record.amount
        ) current.drop(1) else current
        return (listOf(record) + rest).take(max)
    }
}
