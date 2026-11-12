package com.tms.tourcollection

import android.app.*
import android.os.Bundle
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Student(val id: Long, val name: String, val section: String, val amount: Long)

class Store(context: android.content.Context) : SQLiteOpenHelper(context, "tour_collection.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE students(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, section TEXT NOT NULL, amount INTEGER NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
    fun all(): List<Student> {
        val out = mutableListOf<Student>()
        readableDatabase.rawQuery("SELECT id,name,section,amount FROM students ORDER BY section,name COLLATE NOCASE", null).use { c ->
            while (c.moveToNext()) out.add(Student(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3)))
        }
        return out
    }
    fun save(id: Long?, name: String, section: String, amount: Long) {
        val v = ContentValues()
        v.put("name", name.trim()); v.put("section", section); v.put("amount", amount)
        if (id == null) writableDatabase.insert("students", null, v) else writableDatabase.update("students", v, "id=?", arrayOf(id.toString()))
    }
    fun delete(id: Long) { writableDatabase.delete("students", "id=?", arrayOf(id.toString())) }
}

class MainActivity : ComponentActivity() {
    private lateinit var store: Store
    private lateinit var root: LinearLayout
    private var exportRows: List<Student> = emptyList()
    private val csvCreator = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> if (uri != null) writeCsv(uri) }
    private val pdfCreator = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri -> if (uri != null) writePdf(uri) }
    private val navy = Color.rgb(16, 27, 51)
    private val blue = Color.rgb(49, 87, 213)
    private val ink = Color.rgb(30, 41, 59)
    private val muted = Color.rgb(105, 117, 137)
    private val light = Color.rgb(185, 200, 229)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Color.rgb(246, 248, 252))
        root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(18, 18, 18, 28)
        scroll.addView(root)
        setContentView(scroll)
        render()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun text(s: String, size: Float, color: Int = ink, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = s
        t.textSize = size
        t.setTextColor(color)
        if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
        return t
    }

    private fun button(label: String, action: () -> Unit, filled: Boolean = false): Button {
        val b = Button(this)
        b.text = label
        b.isAllCaps = false
        b.textSize = 14f
        b.setTextColor(if (filled) Color.WHITE else blue)
        b.backgroundTintList = android.content.res.ColorStateList.valueOf(if (filled) blue else Color.rgb(232, 238, 255))
        b.setOnClickListener { action() }
        return b
    }

    private fun card(): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.setPadding(dp(16), dp(14), dp(16), dp(14))
        c.setBackgroundColor(Color.WHITE)
        c.elevation = dp(2).toFloat()
        return c
    }

    private fun space(h: Int) {
        root.addView(Space(this), LinearLayout.LayoutParams(1, dp(h)))
    }

    private fun render() {
        root.removeAllViews()
        val rows = store.all()
        val total = rows.sumOf { it.amount }

        // ---- Header ----
        val hero = LinearLayout(this)
        hero.orientation = LinearLayout.VERTICAL
        hero.setPadding(dp(20), dp(22), dp(20), dp(22))
        hero.setBackgroundColor(navy)
        hero.addView(text("TMS  /  TOUR ACCOUNTS", 11f, light, true))
        hero.addView(text("Tour Collection", 27f, Color.WHITE, true))
        hero.addView(text("CLASS 9  •  OFFLINE REGISTER", 11f, light))
        val bigTotal = text("₹$total", 32f, Color.WHITE, true)
        bigTotal.setPadding(0, dp(18), 0, 0)
        hero.addView(bigTotal)
        hero.addView(text("TOTAL COLLECTION  ·  ${rows.size} STUDENTS", 10f, light, true))
        root.addView(hero)

        space(14)
        root.addView(text("Collection by section", 18f, ink, true))
        space(8)

        // ---- Section summary ----
        val summary = card()
        for (sec in listOf("A", "B", "C")) {
            val count = rows.count { it.section == sec }
            val sum = rows.filter { it.section == sec }.sumOf { it.amount }
            val line = LinearLayout(this)
            line.orientation = LinearLayout.HORIZONTAL
            line.gravity = Gravity.CENTER_VERTICAL
            line.addView(text("SECTION $sec", 14f, ink, true), LinearLayout.LayoutParams(0, -2, 1f))
            line.addView(text("$count students   ·   ₹$sum", 14f, blue, true))
            summary.addView(line)
            if (sec != "C") {
                val divider = View(this)
                divider.setBackgroundColor(Color.rgb(235, 239, 246))
                val lp = LinearLayout.LayoutParams(-1, dp(1))
                lp.setMargins(0, dp(11), 0, dp(11))
                divider.layoutParams = lp
                summary.addView(divider)
            }
        }
        root.addView(summary)

        space(14)
        root.addView(button("＋  Add student entry", { showEditor(null) }, true), LinearLayout.LayoutParams(-1, dp(52)))
        space(8)

        // ---- Export buttons ----
        val actions = LinearLayout(this)
        actions.orientation = LinearLayout.HORIZONTAL
        actions.addView(button("Download PDF", { exportRows = store.all(); pdfCreator.launch("Tour_Collection_Report.pdf") }), LinearLayout.LayoutParams(0, dp(48), 1f))
        actions.addView(Space(this), LinearLayout.LayoutParams(dp(8), 1))
        actions.addView(button("Export Excel CSV", { exportRows = store.all(); csvCreator.launch("Tour_Collection_Records.csv") }), LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(actions)

        space(18)
        root.addView(text("Student records", 18f, ink, true))
        root.addView(text("Tap Edit to change an entry or Delete to remove it.", 12f, muted))
        space(8)

        // ---- Student list ----
        if (rows.isEmpty()) {
            val empty = card()
            empty.addView(text("No entries yet", 16f, ink, true))
            empty.addView(text("Add the first student's name, section and amount.", 13f, muted))
            root.addView(empty)
        } else {
            for (student in rows) {
                val item = card()
                val top = LinearLayout(this)
                top.orientation = LinearLayout.HORIZONTAL
                top.gravity = Gravity.CENTER_VERTICAL
                val left = LinearLayout(this)
                left.orientation = LinearLayout.VERTICAL
                left.addView(text(student.name, 16f, ink, true))
                left.addView(text("CLASS 9 · SECTION ${student.section}", 11f, muted))
                top.addView(left, LinearLayout.LayoutParams(0, -2, 1f))
                top.addView(text("₹${student.amount}", 17f, blue, true))
                item.addView(top)

                val controls = LinearLayout(this)
                controls.orientation = LinearLayout.HORIZONTAL
                controls.gravity = Gravity.END
                controls.addView(button("Edit", { showEditor(student) }))
                controls.addView(button("Delete", { confirmDelete(student) }))
                item.addView(controls)

                root.addView(item)
                space(8)
            }
        }
        space(10)
        root.addView(text("Data stays on this phone. Export a backup regularly.", 11f, muted))
    }

    private fun showEditor(existing: Student?) {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(8), dp(20), 0)

        val name = EditText(this)
        name.hint = "Student full name"
        name.setSingleLine(true)
        name.setText(existing?.name ?: "")

        val amount = EditText(this)
        amount.hint = "Amount received (₹)"
        amount.inputType = 2
        amount.setSingleLine(true)
        amount.setText(existing?.amount?.toString() ?: "")

        val sections = listOf("A", "B", "C")
        val section = Spinner(this)
        section.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, sections)
        section.setSelection(sections.indexOf(existing?.section ?: "A").coerceAtLeast(0))

        box.addView(text("STUDENT NAME", 11f, muted, true)); box.addView(name)
        box.addView(text("SECTION", 11f, muted, true)); box.addView(section)
        box.addView(text("AMOUNT RECEIVED", 11f, muted, true)); box.addView(amount)

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Add student" else "Edit student")
            .setView(box)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val n = name.text.toString().trim()
                val a = amount.text.toString().toLongOrNull()
                if (n.isBlank()) name.error = "Enter student name"
                else if (a == null || a < 0) amount.error = "Enter a valid amount"
                else {
                    store.save(existing?.id, n, section.selectedItem.toString(), a)
                    dialog.dismiss()
                    render()
                }
            }
        }
        dialog.show()
    }

    private fun confirmDelete(s: Student) {
        AlertDialog.Builder(this)
            .setTitle("Delete ${s.name}?")
            .setMessage("This entry will be permanently removed from this phone.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ -> store.delete(s.id); render() }
            .show()
    }

    private fun writeCsv(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { stream ->
                OutputStreamWriter(stream, Charsets.UTF_8).use { w ->
                    w.write("Student Name,Class,Section,Amount (INR)\n")
                    exportRows.forEach { s -> w.write("\"${s.name.replace("\"", "\"\"")}\",9,${s.section},${s.amount}\n") }
                    w.write("\nSection,Total (INR)\n")
                    listOf("A", "B", "C").forEach { sec -> w.write("$sec,${exportRows.filter { it.section == sec }.sumOf { it.amount }}\n") }
                    w.write("Grand Total,${exportRows.sumOf { it.amount }}\n")
                }
            }
            Toast.makeText(this, "Excel-compatible CSV exported", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun writePdf(uri: Uri) {
        val doc = PdfDocument()
        try {
            val pageW = 595
            val pageH = 842
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            var pageNo = 1
            var y = 54f
            var page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
            var canvas = page.canvas

            fun header() {
                paint.color = navy
                canvas.drawRect(0f, 0f, pageW.toFloat(), 112f, paint)
                paint.color = Color.WHITE
                paint.textSize = 22f
                paint.typeface = Typeface.DEFAULT_BOLD
                canvas.drawText("TOUR COLLECTION REPORT", 36f, 48f, paint)
                paint.textSize = 10f
                paint.typeface = Typeface.DEFAULT
                val stamp = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
                canvas.drawText("CLASS 9  ·  GENERATED $stamp", 36f, 72f, paint)
                paint.color = ink
                paint.textSize = 10f
                canvas.drawText("STUDENT NAME", 36f, 140f, paint)
                canvas.drawText("SECTION", 340f, 140f, paint)
                canvas.drawText("AMOUNT (₹)", 455f, 140f, paint)
                y = 162f
            }

            fun newPage() {
                doc.finishPage(page)
                pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
                canvas = page.canvas
                header()
            }

            header()
            for (s in exportRows) {
                if (y > 770f) newPage()
                paint.color = ink
                paint.textSize = 11f
                paint.typeface = Typeface.DEFAULT
                canvas.drawText(s.name.take(38), 36f, y, paint)
                canvas.drawText(s.section, 350f, y, paint)
                canvas.drawText("₹${s.amount}", 455f, y, paint)
                paint.color = Color.rgb(232, 236, 243)
                canvas.drawLine(36f, y + 9, 555f, y + 9, paint)
                y += 27f
            }

            if (y > 700f) newPage()
            y += 24f
            paint.color = navy
            paint.textSize = 12f
            paint.typeface = Typeface.DEFAULT_BOLD
            canvas.drawText("SECTION TOTALS", 36f, y, paint)
            y += 24f
            for (sec in listOf("A", "B", "C")) {
                paint.color = ink
                paint.textSize = 11f
                canvas.drawText("Section $sec", 36f, y, paint)
                canvas.drawText("₹${exportRows.filter { it.section == sec }.sumOf { it.amount }}", 455f, y, paint)
                y += 22f
            }
            y += 10f
            paint.color = blue
            paint.textSize = 15f
            paint.typeface = Typeface.DEFAULT_BOLD
            canvas.drawText("GRAND TOTAL", 36f, y, paint)
            canvas.drawText("₹${exportRows.sumOf { it.amount }}", 455f, y, paint)
            doc.finishPage(page)

            contentResolver.openOutputStream(uri)?.use { doc.writeTo(it) }
            Toast.makeText(this, "PDF report exported", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "PDF export failed: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            doc.close()
        }
    }
}
