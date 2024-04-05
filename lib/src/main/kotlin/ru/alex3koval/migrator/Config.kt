package ru.alex3koval.migrator

import kotlinx.serialization.Serializable

@Serializable
internal data class Config(val folder: String, val connection: String, val user: String, val password: String)