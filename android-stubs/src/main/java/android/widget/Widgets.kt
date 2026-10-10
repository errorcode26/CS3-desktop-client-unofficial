package android.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import com.lagradost.cloudstream3.desktop.shadowui.ShadowUi

open class RadioGroup : LinearLayout {
    constructor() : super()
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: Any?) : super(context, attrs)

    var checkedRadioButtonId: Int = -1
        set(value) {
            field = value
            ShadowUi.bump()
        }

    fun check(id: Int) {
        checkedRadioButtonId = id
    }

    fun clearCheck() {
        checkedRadioButtonId = -1
    }
}

open class Space : View {
    constructor() : super()
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: Any?) : super(context, attrs)
}

open class ImageButton : ImageView {
    constructor() : super()
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: Any?) : super(context, attrs)
}

open class ProgressBar : View {
    constructor() : super()
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    var progress: Int = 0
        set(value) {
            field = value
            ShadowUi.bump()
        }
    var max: Int = 100
        set(value) {
            field = value
            ShadowUi.bump()
        }
    var isIndeterminate: Boolean = false
        set(value) {
            field = value
            ShadowUi.bump()
        }
}

open class RelativeLayout : ViewGroup {
    constructor() : super()
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: Any?) : super(context, attrs)

    open class LayoutParams : ViewGroup.MarginLayoutParams {
        constructor(c: Context?, attrs: Any?) : super(c, attrs)
        constructor(w: Int, h: Int) : super(w, h)
        constructor(source: ViewGroup.LayoutParams?) : super(source)
        constructor(source: ViewGroup.MarginLayoutParams?) : super(source)
        constructor(source: LayoutParams?) : super(source)

        open fun addRule(verb: Int) {}
        open fun addRule(verb: Int, subject: Int) {}
        open fun removeRule(verb: Int) {}
    }
}

interface ListAdapter {
    fun getCount(): Int
    fun getItem(position: Int): Any?
    fun getItemId(position: Int): Long
    fun getView(position: Int, convertView: View?, parent: ViewGroup?): View?
}

open class ListView : ViewGroup {
    constructor() : super()
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: Any?) : super(context, attrs)

    var adapter: ListAdapter? = null
        set(value) {
            field = value
            ShadowUi.bump()
        }

    interface OnItemClickListener {
        fun onItemClick(parent: ViewGroup?, view: View?, position: Int, id: Long)
    }

    var itemClickListener: OnItemClickListener? = null

    fun setOnItemClickListener(listener: OnItemClickListener?) {
        itemClickListener = listener
    }

    fun performItemClick(position: Int): Boolean {
        itemClickListener?.onItemClick(this, getChildAt(position), position, position.toLong())
        return true
    }
}

open class AdapterView<T> : ViewGroup {
    constructor() : super()
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: Any?) : super(context, attrs)

    interface OnItemSelectedListener {
        fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long)
        fun onNothingSelected(parent: AdapterView<*>?)
    }

    var onItemSelectedListener: OnItemSelectedListener? = null
    var selectedItemPosition: Int = 0
    open var adapter: Any? = null
}

open class Spinner : AdapterView<Any?> {
    constructor() : super()
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: Any?) : super(context, attrs)

    var prompt: CharSequence? = null

    override var adapter: Any? = null
        set(value) {
            field = value
            ShadowUi.bump()
        }

    fun setSelection(position: Int) {
        selectedItemPosition = position
        onItemSelectedListener?.onItemSelected(this, null, position, position.toLong())
        ShadowUi.bump()
    }

    fun setSelection(position: Int, animate: Boolean) {
        setSelection(position)
    }

    fun getSelectedItem(): Any? {
        val a = adapter
        if (a is ListAdapter && selectedItemPosition in 0 until a.getCount()) return a.getItem(selectedItemPosition)
        if (a is ArrayAdapter<*> && selectedItemPosition in 0 until a.count) return a.getItem(selectedItemPosition)
        return null
    }
}

open class ArrayAdapter<T> : ListAdapter {
    val items: MutableList<T>

    constructor(context: Context?, resource: Int) { this.items = ArrayList() }
    constructor(context: Context?, resource: Int, objects: Array<T>) { this.items = objects.toMutableList() }
    constructor(context: Context?, resource: Int, objects: List<T>) { this.items = objects.toMutableList() }
    constructor(context: Context?, resource: Int, textViewResourceId: Int, objects: List<T>) { this.items = objects.toMutableList() }

    @get:JvmName("getCountProperty")
    val count: Int get() = items.size
    override fun getCount(): Int = items.size
    override fun getItem(position: Int): T? = items.getOrNull(position)
    override fun getItemId(position: Int): Long = position.toLong()
    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View? = null

    fun setDropDownViewResource(resource: Int) {}
    fun add(item: T) { items.add(item); ShadowUi.bump() }
    fun addAll(vararg items: T) { this.items.addAll(items); ShadowUi.bump() }
    fun addAll(collection: Collection<T>) { this.items.addAll(collection); ShadowUi.bump() }
    fun clear() { items.clear(); ShadowUi.bump() }
    fun notifyDataSetChanged() { ShadowUi.bump() }
}
