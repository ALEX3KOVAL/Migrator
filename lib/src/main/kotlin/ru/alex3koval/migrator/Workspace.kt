package ru.alex3koval.migrator

import org.intellij.lang.annotations.Language
import org.reflections.Reflections
import java.sql.Connection
import java.sql.DriverManager

internal class Workspace(
    private val datasourceName: String,
    private val config: Config,
    private val mode: MigrationMode = MigrationMode.DEFAULT
) {
    private val jdbc: Connection by lazy {
        log("Connecting to ${config.connection}")
        val conn = DriverManager.getConnection(config.connection, config.user, config.password)

        if (conn!!.isValid(0)) {
            log("${ConsoleColor.GREEN}Connection is established ${ConsoleColor.RESET}")
        } else {
            throw RuntimeException("${ConsoleColor.RED}Can't connect to: ${config.connection} ${ConsoleColor.RESET}")
        }

        conn
    }
    private var locked = false
    private val allMigrations: Map<Int, MigrationInfo> by lazy {
        val reflections = Reflections(migrationPackage)
        val migrations = mutableMapOf<Int, MigrationInfo>()

        val clazzes = reflections.getSubTypesOf(Migration::class.java)
        clazzes.forEach {
            val migrationId: MigrationID = it.getAnnotation(MigrationID::class.java)
                ?: throw RuntimeException("MigrationId not found for ${it.canonicalName}")

            migrations
                .putIfAbsent(
                    migrationId.value,
                    MigrationInfo(migrationId.value, it)
                )
                .also { value ->
                    if (value != null) {
                        throw RuntimeException("\nFound two migrations with same id: ${migrationId.value}. \nM1: ${it.canonicalName} \nM2: ${value.clazz.canonicalName}")
                    }
                }
        }

        migrations
    }

    private val newMigrations: List<MigrationInfo> by lazy {
        val result = mutableListOf<MigrationInfo>()
        val committedMigrations = allCommittedMigrations()
        allMigrations.forEach {
            if (!committedMigrations.contains(it.key)) result.add(it.value)
        }

        result.apply { sortBy { it.id } }
    }

    private val migrationPackage: String
        get() = config.folder

    private fun lock(): Boolean {
        if (locked) return true

        jdbc.createStatement().also {
            val result = it.executeQuery("SELECT pg_try_advisory_lock(1581) as lock")
            result.next()
            val lock = result.getBoolean("lock")

            if (!lock) {
                println("${ConsoleColor.RED}Another process is working with migration.${ConsoleColor.RESET}")
                return false
            }
            log("${ConsoleColor.GREEN}DB lock is caught${ConsoleColor.RESET}")
        }

        locked = true

        return true
    }

    private fun unlock() {
        jdbc.prepareStatement("SELECT pg_advisory_unlock(1581)").execute()
        locked = false
    }

    fun run(): Boolean {
        log("${ConsoleColor.YELLOW}" + "#".repeat(20) + "   $datasourceName   " + "#".repeat(20) + "${ConsoleColor.RESET}")

        init()
        lock()

        var wellDone = true

        for (mcs in newMigrations) {
            val context = MigrationContext(jdbc)
            val m = mcs.clazz.constructors.first().newInstance() as Migration

            log("\n${ConsoleColor.BLUE}RUN #${mcs.id} ${m.javaClass}${ConsoleColor.RESET}")

            m.upFunction.invoke(context)

            if (!context.run(mode)) {
                wellDone = false
                break
            }

            commitMigration(mcs.id)
        }

        unlock()
        jdbc.close()

        return wellDone
    }

    private fun init() {
        @Language("SQL")
        val createMigrationTableSql = """
            create table if not exists migrator
                (
                    migration_id integer not null,
                    status       integer not null
                );

            create unique index if not exists migration_id_index
                on migrator (migration_id);
        """.trimMargin()

        jdbc.createStatement().execute(createMigrationTableSql)
    }

    private fun commitMigration(id: Int) {
        jdbc.prepareStatement("INSERT INTO migrator VALUES($id,1)").execute()
        jdbc.commit()
    }

    private fun allCommittedMigrations(): Set<Int> {
        val resultSet = jdbc.prepareStatement("SELECT migration_id FROM migrator ORDER BY migration_id").executeQuery()
        val result = HashSet<Int>()

        while (resultSet.next()) {
            result.add(resultSet.getInt("migration_id"))
        }

        return result
    }

    private fun log(message: String) {
        mode.ifNotSilent { println(message) }
    }
}