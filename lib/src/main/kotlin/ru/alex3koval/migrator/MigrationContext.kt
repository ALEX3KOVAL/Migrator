package ru.alex3koval.migrator

import java.sql.Connection

class MigrationContext(val jdbc: Connection) {
    private val queries: MutableList<String> = mutableListOf()

    fun run(mode: MigrationMode = MigrationMode.DEFAULT): Boolean {
        try {
            jdbc.autoCommit = false

            queries.forEach {
                mode.ifNotSilent { println("EXECUTE QUERY: $it") }
                jdbc.prepareStatement(it).execute()
            }

            jdbc.commit()
        } catch (e: Exception) {
            println("${ConsoleColor.RED}FAILED${ConsoleColor.RESET}")
            println("${ConsoleColor.RED}${e.message}${ConsoleColor.RESET}")

            jdbc.rollback()

            println("ROLLBACK MIGRATION\n")

            return false
        }

        mode.ifNotSilent { println("${ConsoleColor.GREEN}SUCCESS${ConsoleColor.RESET}\n") }
        return true
    }
}