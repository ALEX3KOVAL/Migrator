package ru.alex3koval.migrator

data class MigrationInfo(val id: Int, val clazz: Class<out Migration>, val isShadow: Boolean = false)