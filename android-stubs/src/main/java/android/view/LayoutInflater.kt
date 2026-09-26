package android.view

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import org.xmlpull.v1.XmlPullParser

class LayoutInflater private constructor() {

    fun inflate(parser: XmlPullParser?, root: ViewGroup?): View? =
        inflate(parser, root, false)

    fun inflate(parser: XmlPullParser?, root: ViewGroup?, attachToRoot: Boolean): View? {
        if (parser == null) return root
        return try {
            var current: View? = null
            val stack = ArrayDeque<View>()
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        val view = createView(parser.name ?: "View") ?: View()
                        applyAttributes(view, parser)
                        val parent = stack.lastOrNull() as? ViewGroup
                        if (parent != null) {
                            parent.addView(view)
                        } else if (current == null) {
                            current = view
                        }
                        stack.addLast(view)
                    }
                    XmlPullParser.END_TAG -> {
                        if (stack.isNotEmpty()) stack.removeLast()
                    }
                }
                event = parser.next()
            }
            if (attachToRoot && root != null && current != null) {
                root.addView(current)
                root
            } else {
                current ?: root
            }
        } catch (t: Throwable) {
            root
        }
    }

    private fun createView(name: String): View? {
        val tag = name.substringAfterLast('.')
        return when (tag) {
            "TextView" -> android.widget.TextView()
            "EditText", "AutoCompleteTextView" -> android.widget.EditText()
            "CheckBox" -> android.widget.CheckBox()
            "Switch", "SwitchCompat" -> android.widget.Switch()
            "RadioButton" -> android.widget.RadioButton()
            "RadioGroup" -> android.widget.RadioGroup()
            "Button" -> android.widget.Button()
            "ImageButton" -> android.widget.ImageButton()
            "ImageView" -> android.widget.ImageView()
            "ProgressBar" -> android.widget.ProgressBar()
            "Space" -> android.widget.Space()
            "LinearLayout" -> android.widget.LinearLayout()
            "RelativeLayout" -> android.widget.RelativeLayout()
            "ScrollView", "NestedScrollView", "HorizontalScrollView" -> android.widget.ScrollView(null)
            "FrameLayout" -> android.widget.FrameLayout(null)
            "ListView" -> android.widget.ListView()
            "RecyclerView" -> androidx.recyclerview.widget.RecyclerView()
            else -> View()
        }
    }

    private fun applyAttributes(view: View, parser: XmlPullParser) {
        val attrs = parser as? AttributeSet ?: return
        val ns = "http://schemas.android.com/apk/res/android"

        view.id = attrs.getAttributeResourceValue(ns, "id", 0)
        if (view.id == 0) {
            view.id = attrs.getAttributeResourceValue(null, "id", 0)
        }

        fun attrString(name: String): String? {
            val v = attrs.getAttributeValue(ns, name) ?: return null
            return v
        }

        when (view) {
            is android.widget.TextView -> {
                attrString("text")?.let { view.setText(it) }
                attrString("hint")?.let { view.setHint(it) }
                attrString("textColor")?.let { c ->
                    runCatching { view.setTextColor(Color.parseColor(c)) }
                }
                attrs.getAttributeValue(ns, "textSize")?.let { s ->
                    s.toFloatOrNull()?.let { view.setTextSize(it) }
                }
                if (attrs.getAttributeBooleanValue(ns, "textStyleBold", false) ||
                    attrs.getAttributeValue(ns, "textStyle") == "1"
                ) {
                    view.setTypeface(null, Typeface.BOLD)
                }
            }
            is android.widget.CompoundButton -> {
                view.isChecked = attrs.getAttributeBooleanValue(ns, "checked", false)
            }
            is android.widget.LinearLayout -> {
                view.orientation = attrs.getAttributeValue(ns, "orientation")?.toIntOrNull()
                    ?: android.widget.LinearLayout.VERTICAL
            }
        }

        attrString("minHeight")?.let { it.toFloatOrNull()?.let { h -> view.minHeight = h.toInt() } }
        attrString("background")?.let { bg ->
            runCatching { view.background = ColorDrawable(Color.parseColor(bg)) }
        }
    }

    companion object {
        @JvmStatic
        fun from(context: Context?): LayoutInflater = LayoutInflater()
    }
}
