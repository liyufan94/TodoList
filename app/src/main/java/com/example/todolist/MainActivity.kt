package com.example.todolist

import android.app.DatePickerDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

// 数据模型
data class TodoItem(
    val id: Long,
    val name: String,
    val date: Date
)

class MainActivity : AppCompatActivity() {

    private val todoList = mutableListOf<TodoItem>()
    private lateinit var adapter: TodoAdapter
    private var nextId = 1L

    private val prefs by lazy {
        getSharedPreferences("todo_prefs", Context.MODE_PRIVATE)
    }
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        val etName = findViewById<EditText>(R.id.etName)
        val etDate = findViewById<EditText>(R.id.etDate)
        val btnAdd = findViewById<Button>(R.id.btnAdd)

        // 先从本地加载数据，并按日期排序
        loadFromPrefs()
        sortList()

        adapter = TodoAdapter(todoList) { item ->
            todoList.remove(item)
            adapter.notifyDataSetChanged()
            saveToPrefs()   // 删除后立即保存
            Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show()
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // ★ 点击日期框，弹出日历
        etDate.setOnClickListener {
            showDatePicker(etDate)
        }

        btnAdd.setOnClickListener {
            val name = etName.text.toString().trim()
            val dateStr = etDate.text.toString().trim()

            if (name.isEmpty()) {
                Toast.makeText(this, "请输入名称", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val date = parseDate(dateStr)
            if (date == null) {
                Toast.makeText(this, "请选择日期", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            todoList.add(TodoItem(nextId++, name, date))
            sortList()      // 添加后重新排序
            adapter.notifyDataSetChanged()
            saveToPrefs()   // 添加后立即保存

            etName.text.clear()
            etDate.text.clear()
            Toast.makeText(this, "已添加", Toast.LENGTH_SHORT).show()
        }
    }

    // ========== 弹出日期选择器 ==========
    private fun showDatePicker(target: EditText) {
        val calendar = Calendar.getInstance()

        // 如果输入框里已经有合法日期，就从那个日期开始
        val existing = target.text.toString().trim()
        if (existing.isNotEmpty()) {
            parseDate(existing)?.let { calendar.time = it }
        }

        val dialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                // month 从 0 开始，Calendar.set 也从 0 开始，正好对上，不用 +1
                val picked = Calendar.getInstance()
                picked.set(year, month, dayOfMonth)
                target.setText(dateFormat.format(picked.time))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        // 禁止选择过去的日期（可选，如不需要可删掉这一行）
        dialog.datePicker.minDate = System.currentTimeMillis() - 1000

        dialog.show()
    }

    private fun parseDate(input: String): Date? {
        return try {
            dateFormat.isLenient = false
            dateFormat.parse(input)
        } catch (e: Exception) {
            null
        }
    }

    // ========== 按日期排序（最早的在前） ==========
    private fun sortList() {
        todoList.sortBy { it.date.time }
    }

    // ========== 持久化：保存 ==========
    private fun saveToPrefs() {
        val array = JSONArray()
        for (item in todoList) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("name", item.name)
            obj.put("date", dateFormat.format(item.date))
            array.put(obj)
        }
        prefs.edit()
            .putString("todo_list", array.toString())
            .putLong("next_id", nextId)
            .apply()
    }

    // ========== 持久化：加载 ==========
    private fun loadFromPrefs() {
        todoList.clear()
        val json = prefs.getString("todo_list", null) ?: return
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.getLong("id")
                val name = obj.getString("name")
                val date = dateFormat.parse(obj.getString("date")) ?: continue
                todoList.add(TodoItem(id, name, date))
            }
            nextId = prefs.getLong("next_id", 1L)
            if (nextId <= (todoList.maxOfOrNull { it.id } ?: 0L)) {
                nextId = (todoList.maxOfOrNull { it.id } ?: 0L) + 1
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

// 适配器
class TodoAdapter(
    private val items: List<TodoItem>,
    private val onDelete: (TodoItem) -> Unit
) : RecyclerView.Adapter<TodoAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvName)
        val tvDate: TextView = view.findViewById(R.id.tvDate)
        val tvDays: TextView = view.findViewById(R.id.tvDays)
        val btnDelete: Button = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_todo, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        holder.tvName.text = item.name

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        holder.tvDate.text = sdf.format(item.date)

        // 计算距离今天的天数
        val today = Calendar.getInstance()
        today.set(Calendar.HOUR_OF_DAY, 0)
        today.set(Calendar.MINUTE, 0)
        today.set(Calendar.SECOND, 0)
        today.set(Calendar.MILLISECOND, 0)

        val target = Calendar.getInstance()
        target.time = item.date
        target.set(Calendar.HOUR_OF_DAY, 0)
        target.set(Calendar.MINUTE, 0)
        target.set(Calendar.SECOND, 0)
        target.set(Calendar.MILLISECOND, 0)

        val diffMillis = target.timeInMillis - today.timeInMillis
        val days = TimeUnit.MILLISECONDS.toDays(diffMillis).toInt()
        val clamped = days.coerceIn(-127, 128)

        holder.tvDays.text = when {
            clamped > 0 -> "还有 $clamped 天"
            clamped < 0 -> "已过 ${-clamped} 天"
            else -> "就是今天"
        }

        holder.btnDelete.setOnClickListener {
            onDelete(item)
        }
    }

    override fun getItemCount() = items.size
}