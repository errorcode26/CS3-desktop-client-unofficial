package android.content.res

import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

class AndroidXmlResourceParser(xmlContent: String) : XmlResourceParser {
    private sealed class Event(val type: Int) {
        object StartDocument : Event(XmlPullParser.START_DOCUMENT)
        class StartTag(val element: Element) : Event(XmlPullParser.START_TAG)
        class EndTag(val name: String) : Event(XmlPullParser.END_TAG)
        class Text(val text: String) : Event(XmlPullParser.TEXT)
        object EndDocument : Event(XmlPullParser.END_DOCUMENT)
    }

    private val events = mutableListOf<Event>()
    private var eventIndex = 0

    init {
        try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
            }
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(InputSource(StringReader(xmlContent)))
            events.add(Event.StartDocument)
            doc.documentElement?.let { buildEvents(it) }
            events.add(Event.EndDocument)
        } catch (_: Throwable) {
            events.add(Event.StartDocument)
            events.add(Event.EndDocument)
        }
    }

    private fun buildEvents(element: Element) {
        events.add(Event.StartTag(element))
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            when (child.nodeType) {
                Node.ELEMENT_NODE -> buildEvents(child as Element)
                Node.TEXT_NODE -> {
                    val t = child.textContent?.trim()
                    if (!t.isNullOrEmpty()) {
                        events.add(Event.Text(t))
                    }
                }
            }
        }
        events.add(Event.EndTag(element.tagName))
    }

    private val currentEvent: Event
        get() = events.getOrElse(eventIndex) { Event.EndDocument }

    private val currentElement: Element?
        get() = (currentEvent as? Event.StartTag)?.element

    override fun next(): Int {
        if (eventIndex < events.size - 1) {
            eventIndex++
        }
        return getEventType()
    }

    override fun getEventType(): Int = currentEvent.type

    override fun getName(): String? = when (val ev = currentEvent) {
        is Event.StartTag -> ev.element.localName ?: ev.element.tagName
        is Event.EndTag -> ev.name.substringAfterLast(':')
        else -> null
    }

    override fun getText(): String? = (currentEvent as? Event.Text)?.text

    override fun getAttributeCount(): Int = currentElement?.attributes?.length ?: 0

    override fun getAttributeName(index: Int): String? {
        val attr = currentElement?.attributes?.item(index) ?: return null
        return attr.localName ?: attr.nodeName.substringAfterLast(':')
    }

    override fun getAttributeNamespace(index: Int): String? {
        return currentElement?.attributes?.item(index)?.namespaceURI ?: ""
    }

    override fun getAttributeValue(index: Int): String? {
        return currentElement?.attributes?.item(index)?.nodeValue
    }

    override fun getAttributeValue(namespace: String?, name: String): String? {
        val elem = currentElement ?: return null
        if (!namespace.isNullOrEmpty()) {
            val v = elem.getAttributeNS(namespace, name)
            if (!v.isNullOrEmpty()) return v
        }
        val prefixed = elem.getAttribute("android:$name")
        if (prefixed.isNotEmpty()) return prefixed
        val appPrefixed = elem.getAttribute("app:$name")
        if (appPrefixed.isNotEmpty()) return appPrefixed
        val direct = elem.getAttribute(name)
        if (direct.isNotEmpty()) return direct
        return null
    }

    override fun getAttributeResourceValue(namespace: String?, name: String, defaultValue: Int): Int {
        val raw = getAttributeValue(namespace, name) ?: return defaultValue
        if (raw.startsWith("@+id/") || raw.startsWith("@id/")) {
            return DesktopResourceRegistry.resolveId(raw)
        }
        if (raw.startsWith("@")) {
            val idStr = raw.substringAfter('/')
            return idStr.toIntOrNull() ?: defaultValue
        }
        return raw.toIntOrNull() ?: defaultValue
    }

    override fun getAttributeResourceValue(index: Int, defaultValue: Int): Int {
        val raw = getAttributeValue(index) ?: return defaultValue
        return raw.toIntOrNull() ?: defaultValue
    }

    override fun getAttributeBooleanValue(namespace: String?, name: String, defaultValue: Boolean): Boolean {
        val raw = getAttributeValue(namespace, name) ?: return defaultValue
        return raw.equals("true", ignoreCase = true)
    }

    override fun getAttributeBooleanValue(index: Int, defaultValue: Boolean): Boolean {
        val raw = getAttributeValue(index) ?: return defaultValue
        return raw.equals("true", ignoreCase = true)
    }

    override fun getAttributeIntValue(namespace: String?, name: String, defaultValue: Int): Int {
        val raw = getAttributeValue(namespace, name) ?: return defaultValue
        return raw.toIntOrNull() ?: defaultValue
    }

    override fun getAttributeIntValue(index: Int, defaultValue: Int): Int {
        val raw = getAttributeValue(index) ?: return defaultValue
        return raw.toIntOrNull() ?: defaultValue
    }

    override fun close() {
        events.clear()
        eventIndex = 0
    }
}
