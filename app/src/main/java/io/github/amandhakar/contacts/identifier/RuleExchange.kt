package io.github.amandhakar.contacts.identifier

import org.json.JSONArray
import org.json.JSONObject

/**
 * Rules to and from a file: your categories and rules, never any phone numbers. The reading side treats the file as untrusted:
 * size limits, unknown or broken rules are skipped and reported instead of being imported. Pure code, unit tested.
 */
object RuleExchange {
    const val FORMAT = "contacts-identifier-rules"
    const val VERSION = 1
    const val MAX_CHARS = 2_000_000
    const val MAX_RULES = 5000
    const val MAX_CONDITIONS = 20
    private const val MAX_TEXT = 200
    private const val DEFAULT_COLOR = 0xFF455A64.toInt()

    data class CategorySpec(val name: String, val color: Int)
    data class RuleSpec(val name: String, val category: String, val enabled: Boolean, val conditions: List<Condition>) {
        /** Two rules with the same category and the same conditions do the same thing. */
        val signature: String get() = category.lowercase() + "\n" + ConditionCodec.encode(conditions)
    }
    data class Parsed(val categories: List<CategorySpec>, val rules: List<RuleSpec>, val skipped: List<String>)

    sealed interface Result {
        data class Ok(val parsed: Parsed) : Result
        data class Bad(val message: String) : Result
    }

    /** What an import would do, worked out before anything is changed. */
    data class Plan(
        val newCategories: List<CategorySpec>,
        val toAdd: List<RuleSpec>,
        val duplicates: Int,
        val replace: Boolean,
        val skipped: List<String>,
    )

    // ---------- export ----------

    fun export(categories: List<Category>, rules: List<Rule>, exportedAtMs: Long): String {
        val byId = categories.associateBy { it.id }
        val root = JSONObject()
        root.put("format", FORMAT)
        root.put("version", VERSION)
        root.put("exportedAt", exportedAtMs)
        root.put("categories", JSONArray().also { a ->
            categories.forEach { a.put(JSONObject().put("name", it.name).put("color", hex(it.color))) }
        })
        root.put("rules", JSONArray().also { a ->
            // the array order is the priority order
            rules.forEach { r ->
                val cat = byId[r.categoryId] ?: return@forEach
                a.put(
                    JSONObject().put("name", r.name).put("category", cat.name).put("enabled", r.enabled)
                        .put("conditions", JSONArray().also { c -> r.conditions.forEach { c.put(JSONObject().put("type", it.type.name).put("param", it.param)) } }),
                )
            }
        })
        return root.toString(2)
    }

    private fun hex(color: Int) = "#%06X".format(color and 0xFFFFFF)

    private fun parseColor(s: String?): Int {
        val t = s?.trim()?.removePrefix("#") ?: return DEFAULT_COLOR
        val v = t.toLongOrNull(16) ?: return DEFAULT_COLOR
        return when (t.length) {
            6 -> (0xFF000000 or v).toInt()
            8 -> v.toInt()
            else -> DEFAULT_COLOR
        }
    }

    private fun clean(s: String?): String = (s ?: "").filter { !it.isISOControl() }.trim().take(MAX_TEXT)

    // ---------- import ----------

    fun parse(text: String): Result {
        if (text.length > MAX_CHARS) return Result.Bad("The file is too large to be a rules file.")
        val root = try { JSONObject(text) } catch (e: Exception) { return Result.Bad("That file could not be read as rules (it is not valid JSON).") }
        if (root.optString("format") != FORMAT) return Result.Bad("That file is not an identifier rules file.")
        val version = root.optInt("version", 0)
        if (version > VERSION) return Result.Bad("That file was made by a newer version of the app. Update the app and try again.")
        if (version < 1) return Result.Bad("That rules file has no version.")

        val skipped = ArrayList<String>()
        val cats = ArrayList<CategorySpec>()
        root.optJSONArray("categories")?.let { a ->
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                val name = clean(o.optString("name"))
                if (name.isNotEmpty() && cats.none { it.name.equals(name, true) }) cats += CategorySpec(name, parseColor(o.optString("color")))
            }
        }
        val arr = root.optJSONArray("rules") ?: return Result.Bad("That file has no rules in it.")
        if (arr.length() > MAX_RULES) return Result.Bad("That file has more than $MAX_RULES rules.")

        val rules = ArrayList<RuleSpec>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i)
            if (o == null) { skipped += "Entry ${i + 1} is not a rule"; continue }
            val name = clean(o.optString("name"))
            val category = clean(o.optString("category"))
            if (name.isEmpty()) { skipped += "Entry ${i + 1} has no name"; continue }
            if (category.isEmpty()) { skipped += "\"$name\" has no category"; continue }
            val ca = o.optJSONArray("conditions")
            if (ca == null || ca.length() == 0) { skipped += "\"$name\" has no conditions"; continue }
            if (ca.length() > MAX_CONDITIONS) { skipped += "\"$name\" has more than $MAX_CONDITIONS conditions"; continue }
            val conds = ArrayList<Condition>()
            var unknown: String? = null
            for (j in 0 until ca.length()) {
                val co = ca.optJSONObject(j) ?: continue
                val type = CondType.entries.firstOrNull { it.name == co.optString("type").trim().uppercase() }
                if (type == null) { unknown = co.optString("type"); break }
                conds += Condition(type, if (type.needsParam) clean(co.optString("param")) else "")
            }
            if (unknown != null) { skipped += "\"$name\" uses \"$unknown\", which this version does not know"; continue }
            val spec = RuleSpec(name, category, o.optBoolean("enabled", true), conds)
            val problem = Engine.validate(Rule(0, name, 0, true, 0, conds))
            if (problem != null) { skipped += "\"$name\": $problem"; continue }
            rules += spec
        }
        return Result.Ok(Parsed(cats, rules, skipped))
    }

    // ---------- what an import would change ----------

    /**
     * [replace] = true replaces all current rules with the file's; otherwise the file's rules are added after the current
     * ones, leaving out any that do exactly what a current rule already does.
     */
    fun plan(existingRules: List<Rule>, existingCategories: List<Category>, parsed: Parsed, replace: Boolean): Plan {
        val catNames = existingCategories.associateBy { it.id }
        val have = if (replace) emptySet() else existingRules.mapNotNull { r ->
            catNames[r.categoryId]?.let { RuleSpec(r.name, it.name, r.enabled, r.conditions).signature }
        }.toSet()
        val seen = HashSet<String>()
        val toAdd = ArrayList<RuleSpec>()
        var dups = 0
        for (r in parsed.rules) {
            if (r.signature in have || !seen.add(r.signature)) dups++ else toAdd += r
        }
        // categories the added rules need that do not exist yet; ones named only by a rule get a neutral colour
        val known = existingCategories.map { it.name.lowercase() }.toMutableSet()
        val fresh = ArrayList<CategorySpec>()
        for (r in toAdd) {
            val key = r.category.lowercase()
            if (known.add(key)) fresh += parsed.categories.firstOrNull { it.name.equals(r.category, true) } ?: CategorySpec(r.category, DEFAULT_COLOR)
        }
        return Plan(fresh, toAdd, dups, replace, parsed.skipped)
    }
}
