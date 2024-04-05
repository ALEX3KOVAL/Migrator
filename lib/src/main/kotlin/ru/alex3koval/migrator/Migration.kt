package ru.alex3koval.migrator

annotation class MigrationID(val value: Int)

open class Migration {
    var upFunction: MigrationContext.() -> Unit = {}
    var downFunction: () -> Unit = {}

    fun up(builder: MigrationContext.() -> Unit) {
        upFunction = builder
    }

    fun down(builder: () -> Unit) {
        downFunction = builder
    }
}

enum class MigrationMode {
    SILENT,
    DEFAULT;

    fun ifNotSilent(body: () -> Unit) = if (this != SILENT) body() else Unit
}