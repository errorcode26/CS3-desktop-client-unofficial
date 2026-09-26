package android.widget

import android.content.Context
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
    constructor(context: Context?, attrs: Any?) : super(context, attrs)

    var progress: Int = 0
        set(value) { field = value; ShadowUi.bump() }
    var max: Int = 100
        set(value) { field = value; ShadowUi.bump() }
    var isIndeterminate: Boolean = false
        set(value) { field = value; ShadowUi.bump() }
}

open class RelativeLayout : ViewGroup {
    constructor() : super()
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: Any?) : super(context, attrs)
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
        set(value) { field = value; ShadowUi.bump() }

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
