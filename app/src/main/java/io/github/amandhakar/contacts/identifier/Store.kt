package io.github.amandhakar.contacts.identifier

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import io.github.amandhakar.contacts.data.Prefs
import io.github.amandhakar.contacts.util.PhoneUtil

data class StoredResult(val categoryId: Long?, val ruleId: Long?, val via: Via)

data class FlagRow(val key: String, val number: String, val categoryId: Long)

data class LogRow(
    val id: Long,
    val ts: Long,
    val source: Source,
    val number: String,
    val name: String?,
    val direction: String,
    val callTs: Long,
    val category: String?,
    val via: Via,
    val ruleId: Long?,
    val ruleName: String?,
    val ms: Long,
    val trace: List<TraceEntry>,
    val error: String?,
)

enum class LogFilter(val label: String) { ALL("All"), CATEGORISED("Categorised"), NONE("No category"), ERRORS("Errors") }

/**
 * Everything the identifier keeps, in a private database on this phone: categories, rules, manual flags, the cached
 * result for each call-log row, and the processing log. Nothing here is ever sent anywhere.
 */
class Store private constructor(private val app: Context) : SQLiteOpenHelper(app, "identifier.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE categories(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, color INTEGER NOT NULL, sort INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE rules(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, category_id INTEGER NOT NULL, enabled INTEGER NOT NULL, sort INTEGER NOT NULL, conditions TEXT NOT NULL, error TEXT)")
        db.execSQL("CREATE TABLE flags(num_key TEXT PRIMARY KEY, number TEXT NOT NULL, category_id INTEGER NOT NULL, created INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE results(call_id INTEGER PRIMARY KEY, version INTEGER NOT NULL, category_id INTEGER, rule_id INTEGER, via TEXT NOT NULL)")
        db.execSQL("CREATE TABLE log(id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER NOT NULL, source TEXT NOT NULL, number TEXT NOT NULL, name TEXT, direction TEXT NOT NULL, call_ts INTEGER NOT NULL, category TEXT, via TEXT NOT NULL, rule_id INTEGER, rule_name TEXT, ms INTEGER NOT NULL, trace TEXT NOT NULL, error TEXT)")
        db.execSQL("CREATE INDEX log_ts ON log(ts)")
        createCorrections(db)

        // Starter categories and example rules. The example rules are OFF; the master switch is off too.
        fun cat(name: String, color: Int, sort: Int): Long = db.insert("categories", null, ContentValues().apply {
            put("name", name); put("color", color); put("sort", sort)
        })
        val spam = cat("Spam", 0xFFD32F2F.toInt(), 0)
        cat("Business", 0xFF1976D2.toInt(), 1)
        cat("Delivery", 0xFFF57C00.toInt(), 2)
        cat("Personal", 0xFF388E3C.toInt(), 3)
        fun rule(name: String, cat: Long, sort: Int, conds: List<Condition>) {
            db.insert("rules", null, ContentValues().apply {
                put("name", name); put("category_id", cat); put("enabled", 0); put("sort", sort)
                put("conditions", ConditionCodec.encode(conds))
            })
        }
        rule("Telemarketing prefix (140)", spam, 0, listOf(Condition(CondType.PREFIX, "140")))
        rule("Hidden number", spam, 1, listOf(Condition(CondType.HIDDEN)))
        rule("Unknown caller at night", spam, 2, listOf(Condition(CondType.UNKNOWN), Condition(CondType.TIME_RANGE, "22:00-07:00")))
        rule("Declined twice in a month", spam, 3, listOf(Condition(CondType.DECLINED, "2/30")))
        rule("Rarely answered", spam, 4, listOf(Condition(CondType.ACCEPT_RATE_BELOW, "10/5")))
    }

    private fun createCorrections(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE corrections(id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER NOT NULL, num_key TEXT NOT NULL, number TEXT NOT NULL, name TEXT, " +
                "was_category TEXT, was_via TEXT NOT NULL, was_rule_id INTEGER, was_rule_name TEXT, should_category TEXT, note TEXT NOT NULL)",
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            createCorrections(db)
            // Two example rules for what you did with a number's calls, switched off, under "Spam" if it still exists.
            db.rawQuery("SELECT id FROM categories WHERE name='Spam' LIMIT 1", null).use { c ->
                if (c.moveToFirst()) {
                    val spam = c.getLong(0)
                    val next = db.rawQuery("SELECT COALESCE(MAX(sort),-1)+1 FROM rules", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }
                    fun rule(name: String, sort: Int, cond: Condition) = db.insert("rules", null, ContentValues().apply {
                        put("name", name); put("category_id", spam); put("enabled", 0); put("sort", sort)
                        put("conditions", ConditionCodec.encode(listOf(cond)))
                    })
                    rule("Declined twice in a month", next, Condition(CondType.DECLINED, "2/30"))
                    rule("Rarely answered", next + 1, Condition(CondType.ACCEPT_RATE_BELOW, "10/5"))
                }
            }
        }
    }

    /** Bumped on every change to rules, categories or flags, so cached results know they are out of date. */
    private fun bump() {
        val p = Prefs(app)
        p.identifierVersion = p.identifierVersion + 1
    }

    // ---------- categories ----------

    fun categories(): List<Category> = readableDatabase.query("categories", null, null, null, null, null, "sort, id").use { c ->
        buildList { while (c.moveToNext()) add(Category(c.getLong(0), c.getString(1), c.getInt(2))) }
    }

    fun addCategory(name: String, color: Int): Long {
        val next = readableDatabase.rawQuery("SELECT COALESCE(MAX(sort),-1)+1 FROM categories", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }
        val id = writableDatabase.insert("categories", null, ContentValues().apply { put("name", name.trim()); put("color", color); put("sort", next) })
        bump()
        return id
    }

    fun updateCategory(id: Long, name: String, color: Int) {
        writableDatabase.update("categories", ContentValues().apply { put("name", name.trim()); put("color", color) }, "id=?", arrayOf(id.toString()))
        bump()
    }

    /** Rules that used the category are switched off (they would have nothing to assign); its flags are removed. */
    fun deleteCategory(id: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("flags", "category_id=?", arrayOf(id.toString()))
            db.update("rules", ContentValues().apply { put("enabled", 0); put("error", "Its category was removed") }, "category_id=?", arrayOf(id.toString()))
            db.delete("categories", "id=?", arrayOf(id.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        bump()
    }

    // ---------- rules ----------

    private fun readRules(sql: String, args: Array<String>? = null): List<Rule> = readableDatabase.rawQuery(sql, args).use { c ->
        buildList {
            while (c.moveToNext()) {
                add(Rule(c.getLong(0), c.getString(1), c.getLong(2), c.getInt(3) == 1, c.getInt(4), ConditionCodec.decode(c.getString(5)), c.getString(6)))
            }
        }
    }

    fun rules(): List<Rule> = readRules("SELECT id,name,category_id,enabled,sort,conditions,error FROM rules ORDER BY sort, id")

    fun rule(id: Long): Rule? = readRules("SELECT id,name,category_id,enabled,sort,conditions,error FROM rules WHERE id=?", arrayOf(id.toString())).firstOrNull()

    /** Inserts (id = 0) or updates. Returns the id. */
    fun saveRule(r: Rule): Long {
        val v = ContentValues().apply {
            put("name", r.name.trim()); put("category_id", r.categoryId); put("enabled", if (r.enabled) 1 else 0)
            put("conditions", ConditionCodec.encode(r.conditions)); put("error", r.error)
        }
        val id = if (r.id == 0L) {
            val next = readableDatabase.rawQuery("SELECT COALESCE(MAX(sort),-1)+1 FROM rules", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }
            v.put("sort", next)
            writableDatabase.insert("rules", null, v)
        } else {
            writableDatabase.update("rules", v, "id=?", arrayOf(r.id.toString())); r.id
        }
        bump()
        return id
    }

    fun deleteRule(id: Long) { writableDatabase.delete("rules", "id=?", arrayOf(id.toString())); bump() }

    fun setRuleEnabled(id: Long, on: Boolean) {
        val v = ContentValues().apply { put("enabled", if (on) 1 else 0); if (on) putNull("error") }
        writableDatabase.update("rules", v, "id=?", arrayOf(id.toString()))
        bump()
    }

    /** A rule that turned out to be broken: switch it off and keep the reason. */
    fun breakRule(id: Long, message: String) {
        writableDatabase.update("rules", ContentValues().apply { put("enabled", 0); put("error", message) }, "id=?", arrayOf(id.toString()))
        bump()
    }

    /** Moves a rule up (-1) or down (+1) in the order. */
    fun moveRule(id: Long, delta: Int) {
        val list = rules().toMutableList()
        val i = list.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in list.indices) return
        val moved = list.removeAt(i)
        list.add(j, moved)
        val db = writableDatabase
        db.beginTransaction()
        try {
            list.forEachIndexed { index, r -> db.update("rules", ContentValues().apply { put("sort", index) }, "id=?", arrayOf(r.id.toString())) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        bump()
    }

    // ---------- manual flags ----------

    fun flags(): Map<String, Long> = readableDatabase.query("flags", arrayOf("num_key", "category_id"), null, null, null, null, null).use { c ->
        buildMap { while (c.moveToNext()) put(c.getString(0), c.getLong(1)) }
    }

    fun flagRows(): List<FlagRow> = readableDatabase.query("flags", arrayOf("num_key", "number", "category_id"), null, null, null, null, "created DESC").use { c ->
        buildList { while (c.moveToNext()) add(FlagRow(c.getString(0), c.getString(1), c.getLong(2))) }
    }

    fun flagOf(number: String): Long? = flags()[PhoneUtil.key(number)]

    fun setFlag(number: String, categoryId: Long): Boolean {
        val key = PhoneUtil.key(number)
        if (key.length < 3) return false
        writableDatabase.insertWithOnConflict("flags", null, ContentValues().apply {
            put("num_key", key); put("number", number.trim()); put("category_id", categoryId); put("created", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE)
        bump()
        return true
    }

    fun clearFlag(number: String) { writableDatabase.delete("flags", "num_key=?", arrayOf(PhoneUtil.key(number))); bump() }

    // ---------- cached results, one per call-log row ----------

    fun results(version: Int): Map<Long, StoredResult> =
        readableDatabase.rawQuery("SELECT call_id, category_id, rule_id, via FROM results WHERE version=?", arrayOf(version.toString())).use { c ->
            buildMap {
                while (c.moveToNext()) put(c.getLong(0), StoredResult(if (c.isNull(1)) null else c.getLong(1), if (c.isNull(2)) null else c.getLong(2), Via.valueOf(c.getString(3))))
            }
        }

    fun clearResults() { writableDatabase.delete("results", null, null) }

    // ---------- reported wrong ----------

    fun addCorrection(number: String, name: String?, wasCategory: String?, wasVia: Via, wasRuleId: Long?, wasRuleName: String?, should: String?, note: String) {
        writableDatabase.insert("corrections", null, ContentValues().apply {
            put("ts", System.currentTimeMillis()); put("num_key", PhoneUtil.key(number)); put("number", number); put("name", name)
            put("was_category", wasCategory); put("was_via", wasVia.name)
            if (wasRuleId != null) put("was_rule_id", wasRuleId) else putNull("was_rule_id")
            put("was_rule_name", wasRuleName); put("should_category", should); put("note", note.trim())
        })
    }

    fun corrections(): List<Correction> = readableDatabase.query("corrections", null, null, null, null, null, "id DESC").use { c ->
        buildList {
            while (c.moveToNext()) {
                fun col(n: String) = c.getColumnIndexOrThrow(n)
                add(
                    Correction(
                        c.getLong(col("id")), c.getLong(col("ts")), c.getString(col("number")), c.getString(col("name")),
                        c.getString(col("was_category")), Via.valueOf(c.getString(col("was_via"))),
                        col("was_rule_id").let { if (c.isNull(it)) null else c.getLong(it) },
                        c.getString(col("was_rule_name")), c.getString(col("should_category")), c.getString(col("note")),
                    ),
                )
            }
        }
    }

    fun deleteCorrection(id: Long) { writableDatabase.delete("corrections", "id=?", arrayOf(id.toString())) }

    fun clearCorrections() { writableDatabase.delete("corrections", null, null) }

    fun correctionCount(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM corrections", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    /** For each rule, how many different numbers were reported as wrongly categorised by it. */
    fun wrongCounts(): Map<Long, Int> = readableDatabase.rawQuery(
        "SELECT was_rule_id, COUNT(DISTINCT num_key) FROM corrections WHERE was_rule_id IS NOT NULL GROUP BY was_rule_id", null,
    ).use { c -> buildMap { while (c.moveToNext()) put(c.getLong(0), c.getInt(1)) } }

    // ---------- the processing log ----------

    /** Runs [block] with a writer that stores results and log rows in one transaction. */
    fun batch(block: (Writer) -> Unit) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            block(Writer(db))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    class Writer(private val db: SQLiteDatabase) {
        fun putResult(callId: Long, version: Int, categoryId: Long?, ruleId: Long?, via: Via) {
            db.insertWithOnConflict("results", null, ContentValues().apply {
                put("call_id", callId); put("version", version); put("via", via.name)
                if (categoryId != null) put("category_id", categoryId) else putNull("category_id")
                if (ruleId != null) put("rule_id", ruleId) else putNull("rule_id")
            }, SQLiteDatabase.CONFLICT_REPLACE)
        }

        fun log(source: Source, number: String, name: String?, direction: Direction, callTs: Long, o: Outcome, ms: Long, error: String? = null) {
            db.insert("log", null, ContentValues().apply {
                put("ts", System.currentTimeMillis()); put("source", source.name); put("number", number); put("name", name)
                put("direction", direction.name); put("call_ts", callTs)
                put("category", o.category?.name); put("via", o.via.name)
                if (o.rule != null) { put("rule_id", o.rule.id); put("rule_name", o.rule.name) }
                put("ms", ms)
                put("trace", o.trace.joinToString("\n") { "${it.rule}\t${it.result}".replace("\r", " ") })
                put("error", error ?: o.ruleErrors.takeIf { it.isNotEmpty() }?.joinToString("; ") { it.second })
            })
        }
    }

    fun log(filter: LogFilter, limit: Int): List<LogRow> {
        val where = when (filter) {
            LogFilter.ALL -> null
            LogFilter.CATEGORISED -> "category IS NOT NULL"
            LogFilter.NONE -> "category IS NULL AND error IS NULL"
            LogFilter.ERRORS -> "error IS NOT NULL"
        }
        return readableDatabase.query("log", null, where, null, null, null, "id DESC", limit.toString()).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        LogRow(
                            c.getLong(c.getColumnIndexOrThrow("id")), c.getLong(c.getColumnIndexOrThrow("ts")),
                            Source.valueOf(c.getString(c.getColumnIndexOrThrow("source"))), c.getString(c.getColumnIndexOrThrow("number")),
                            c.getString(c.getColumnIndexOrThrow("name")), c.getString(c.getColumnIndexOrThrow("direction")),
                            c.getLong(c.getColumnIndexOrThrow("call_ts")), c.getString(c.getColumnIndexOrThrow("category")),
                            Via.valueOf(c.getString(c.getColumnIndexOrThrow("via"))),
                            c.getColumnIndexOrThrow("rule_id").let { if (c.isNull(it)) null else c.getLong(it) },
                            c.getString(c.getColumnIndexOrThrow("rule_name")), c.getLong(c.getColumnIndexOrThrow("ms")),
                            c.getString(c.getColumnIndexOrThrow("trace")).lineSequence().filter { it.isNotBlank() }.map {
                                val p = it.split('\t', limit = 2); TraceEntry(p[0], p.getOrElse(1) { "" })
                            }.toList(),
                            c.getString(c.getColumnIndexOrThrow("error")),
                        ),
                    )
                }
            }
        }
    }

    fun logCount(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM log", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    fun trimLog(keep: Int) {
        writableDatabase.execSQL("DELETE FROM log WHERE id <= (SELECT COALESCE(MAX(id),0) FROM log) - ?", arrayOf<Any>(keep))
    }

    fun clearLog() { writableDatabase.delete("log", null, null) }

    /** How many times each rule decided a call, from the log. Shown next to the rule so a rule that never fires stands out. */
    fun ruleHits(): Map<Long, Int> = readableDatabase.rawQuery("SELECT rule_id, COUNT(*) FROM log WHERE via='RULE' AND rule_id IS NOT NULL GROUP BY rule_id", null).use { c ->
        buildMap { while (c.moveToNext()) put(c.getLong(0), c.getInt(1)) }
    }

    companion object {
        @Volatile private var instance: Store? = null
        fun get(ctx: Context): Store = instance ?: synchronized(this) { instance ?: Store(ctx.applicationContext).also { instance = it } }
        const val LOG_KEEP = 2000
    }
}
