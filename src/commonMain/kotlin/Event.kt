package kotlinx.html.org.w3c.dom.events

expect open class Event {
    fun stopPropagation()
    fun preventDefault()
}
