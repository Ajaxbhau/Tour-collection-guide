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

private data class Student(val id: Long, val name: String, val section: String, val amount: Long)

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
        val v = ContentValues().apply { put("name", name.trim()); put("section", section); put("amount", amount) }
        if (id == null) writableDatabase.insert("students", null, v) else writableDatabase.update("students", v, "id=?", arrayOf(id.toString()))
    }
    fun delete(id: Long) { writableDatabase.delete("students", "id=?", arrayOf(id.toString())) }
}

class MainActivity : ComponentActivity() {
    private lateinit var store: Store
    private lateinit var root: LinearLayout
    private var exportRows: List<Student> = emptyList()
    private var exportKind = "csv"
    private val csvCreator = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> if (uri != null) writeCsv(uri) }
    private val pdfCreator = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri -> if (uri != null) writePdf(uri) }
    private val navy = Color.rgb(16, 27, 51)
    private val blue = Color.rgb(49, 87, 213)
    private val ink = Color.rgb(30, 41, 59)
    private val muted = Color.rgb(105, 117, 137)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(246, 248, 252)) }
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18, 18, 18, 28) }
        scroll.addView(root)
        setContentView(scroll)
        render()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun text(s: String, size: Float, color: Int = ink, bold: Boolean = false): TextView = TextView(this).apply {
        text = s; textSize = size; setTextColor(color); if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    private fun button(label: String, action: () -> Unit, filled: Boolean = false): Button = Button(this).apply {
        text = label; isAllCaps = false; textSize = 14f; setTextColor(if (filled) Color.WHITE else blue)
        if (android.os.Build.VERSION.SDK_INT >= 21) backgroundTintList = android.content.res.ColorStateList.valueOf(if (filled) blue else Color.rgb(232, 238, 255))
        setOnClickListener { action() }
    }
    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(14)); setBackgroundColor(Color.WHITE)
        elevation = dp(2).toFloat()
    }
    private fun render() {
        root.removeAllViews()
        val rows = store.all()
        val total = rows.sumOf { it.amount }
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(22), dp(20), dp(22)); setBackgroundColor(navy)
            addView(text("TMS  /  TOUR ACCOUNTS", 11f, Color.rgb(185, 200, 229), true))
            addView(text("Tour Collection", 27f, Color.WHITE, true))
            addView(text("CLASS 9  •  OFFLINE REGISTER", 11f, Color.rgb(185, 200, 229)))
            addView(text("₹${total}", 32f, Color.WHITE, true).apply { setPadding(0, dp(18), 0, 0) })
            addView(text("TOTAL COLLECTION  ·  ${rows.size} STUDENTS", 10f, Color.rgb(185, 200, 229), true))
        })
        space(14)
        addView(text("Collection by section", 18f, ink, true))
        space(8)
        val summary = card()
        listOf("A", "B", "C").forEach { sec ->
            val count = rows.count { it.section == sec }; val sum = rows.filter { it.section == sec }.sumOf { it.amount }
            summary.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                addView(text("SECTION $sec", 14f, ink, true), LinearLayout.LayoutParams(0, -2, 1f))
                addView(text("$count students   ·   ₹$sum", 14f, blue, true))
            })
            if (sec != "C") summary.addView(View(this).apply { setBackgroundColor(Color.rgb(235, 239, 246)); layoutParams = LinearLayout.LayoutParams(-1, dp(1)).apply { setMargins(0, dp(11), 0, dp(11)) } })
        }
        root.addView(summary)
        space(14)
        addView(button("＋  Add student entry", { showEditor(null) }, true), LinearLayout.LayoutParams(-1, dp(52)))
        space(8)
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(button("Download PDF", { exportRows = store.all(); pdfCreator.launch("Tour_Collection_Report.pdf") }), LinearLayout.LayoutParams(0, dp(48), 1f))
        actions.addView(Space(this), LinearLayout.LayoutParams(dp(8), 1))
        actions.addView(button("Export Excel CSV", { exportRows = store.all(); csvCreator.launch("Tour_Collection_Records.csv") }), LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(actions)
        space(18)
        addView(text("Student records", 18f, ink, true))
        addView(text("Tap Edit to change an entry or Delete to remove it.", 12f, muted))
        space(8)
        if (rows.isEmpty()) {
            val empty = card(); empty.addView(text("No entries yet", 16f, ink, true)); empty.addView(text("Add the first student's name, section and amount.", 13f, muted)); root.addView(empty)
        } else rows.forEach { student ->
            val item = card()
            item.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                val left = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
                left.addView(text(student.name, 16f, ink, true)); left.addView(text("CLASS 9 · SECTION ${student.section}", 11f, muted))
                addView(left, LinearLayout.LayoutParams(0, -2, 1f)); addView(text("₹${student.amount}", 17f, blue, true))
            })
            val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
            controls.addView(button("Edit", { showEditor(student) })); controls.addView(button("Delete", { confirmDelete(student) }))
            item.addView(controls); root.addView(item); space(8)
        }
        space(10)
        addView(text("Data stays on this phone. Export a backup regularly.", 11f, muted))
    }
    private fun space(h: Int) { root.addView(Space(this), LinearLayout.LayoutParams(1, dp(h))) }

    private fun showEditor(existing: Student?) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        val name = EditText(this).apply { hint = "Student full name"; setSingleLine(true); setText(existing?.name ?: "") }
        val amount = EditText(this).apply { hint = "Amount received (₹)"; inputType = 2; setSingleLine(true); setText(existing?.amount?.toString() ?: "") }
        val section = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("A", "B", "C")); setSelection(listOf("A", "B", "C").indexOf(existing?.section ?: "A").coerceAtLeast(0)) }
        box.addView(text("STUDENT NAME", 11f, muted, true)); box.addView(name)
        box.addView(text("SECTION", 11f, muted, true)); box.addView(section)
        box.addView(text("AMOUNT RECEIVED", 11f, muted, true)); box.addView(amount)
        AlertDialog.Builder(this).setTitle(if (existing == null) "Add student" else "Edit student").setView(box)
            .setNegativeButton("Cancel", null).setPositiveButton("Save", null).create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val n = name.text.toString().trim(); val a = amount.text.toString().toLongOrNull()
                        if (n.isBlank()) name.error = "Enter student name"
                        else if (a == null || a < 0) amount.error = "Enter a valid amount"
                        else { store.save(existing?.id, n, section.selectedItem.toString(), a); dialog.dismiss(); render() }
                    }
                }
                dialog.show()
            }
    }
    private fun confirmDelete(s: Student) {
        AlertDialog.Builder(this).setTitle("Delete ${s.name}?").setMessage("This entry will be permanently removed from this phone.")
            .setNegativeButton("Cancel", null).setPositiveButton("Delete") { _, _ -> store.delete(s.id); render() }.show()
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
        } catch (e: Exception) { Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show() }
    }
    private fun writePdf(uri: Uri) {
        val doc = PdfDocument()
        try {
            val pageW = 595; val pageH = 842; val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            var pageNo = 1; var y = 54f
            var page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create()); var canvas = page.canvas
            fun header() {
                paint.color = navy; canvas.drawRect(0f, 0f, pageW.toFloat(), 112f, paint)
                paint.color = Color.WHITE; paint.textSize = 22f; paint.typeface = Typeface.create("sans-serif", Typeface.BOLD); canvas.drawText("TOUR COLLECTION REPORT", 36f, 48f, paint)
                paint.textSize = 10f; paint.typeface = Typeface.NORMAL; canvas.drawText("CLASS 9  ·  GENERATED ${SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())}", 36f, 72f, paint)
                paint.color = ink; paint.textSize = 10f; canvas.drawText("STUDENT NAME", 36f, 140f, paint); canvas.drawText("SECTION", 340f, 140f, paint); canvas.drawText("AMOUNT (₹)", 455f, 140f, paint)
                y = 162f
            }
            fun newPage() { doc.finishPage(page); pageNo++; page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create()); canvas = page.canvas; header() }
            header()
            exportRows.forEach { s ->
                if (y > 770f) newPage()
                paint.color = ink; paint.textSize = 11f; paint.typeface = Typeface.NORMAL
                canvas.drawText(s.name.take(38), 36f, y, paint); canvas.drawText(s.section, 350f, y, paint); canvas.drawText("₹${s.amount}", 455f, y, paint)
                paint.color = Color.rgb(232, 236, 243); canvas.drawLine(36f, y + 9, 555f, y + 9, paint); y += 27f
            }
            if (y > 700f) newPage()
            y += 24f; paint.color = navy; paint.textSize = 12f; paint.typeface = Typeface.BOLD; canvas.drawText("SECTION TOTALS", 36f, y, paint); y += 24f
            listOf("A", "B", "C").forEach { sec -> paint.color = ink; paint.textSize = 11f; canvas.drawText("Section $sec", 36f, y, paint); canvas.drawText("₹${exportRows.filter { it.section == sec }.sumOf { it.amount }}", 455f, y, paint); y += 22f }
            y += 10f; paint.color = blue; paint.textSize = 15f; paint.typeface = Typeface.BOLD; canvas.drawText("GRAND TOTAL", 36f, y, paint); canvas.drawText("₹${exportRows.sumOf { it.amount }}", 455f, y, paint)
            doc.finishPage(page)
            contentResolver.openOutputStream(uri)?.use { doc.writeTo(it) }
            Toast.makeText(this, "PDF report exported", Toast.LENGTH_LONG).show()
        } catch (e: Exception) { Toast.makeText(this, "PDF export failed: ${e.message}", Toast.LENGTH_LONG).show() }
        finally { doc.close() }
    }
}
