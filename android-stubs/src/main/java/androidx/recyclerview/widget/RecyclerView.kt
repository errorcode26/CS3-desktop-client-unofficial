package androidx.recyclerview.widget

import android.content.Context
import android.view.View
import android.view.ViewGroup
import com.lagradost.cloudstream3.desktop.shadowui.ShadowUi

open class RecyclerView : ViewGroup {
    constructor() : super()
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: Any?) : super(context, attrs)

    open class ViewHolder(val itemView: View)

    abstract class Adapter<VH : ViewHolder> {
        abstract fun getItemCount(): Int
        abstract fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH
        abstract fun onBindViewHolder(holder: VH, position: Int)
        open fun getItemViewType(position: Int): Int = 0
    }

    var adapter: Adapter<*>? = null
        set(value) { field = value; ShadowUi.bump() }
}
