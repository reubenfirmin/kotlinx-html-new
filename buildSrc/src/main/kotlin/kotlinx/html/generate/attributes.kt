package kotlinx.html.generate

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName

fun String.quote() = "\"$this\""

fun Appendable.attributePseudoDelegate(request: AttributeRequest) {
    val classNamePrefix = request.type.classPrefix
    val className = "${classNamePrefix}Attribute"

    append("internal ")
    variable(Var(request.delegatePropertyName, "Attribute<${request.typeName}>"))
    defineIs(StringBuilder().apply {
        functionCallConsts(className, request.options)
    })
    emptyLine()
}

fun Appendable.attributeProperty(
    repository: Repository,
    attribute: AttributeInfo,
    receiver: String? = null,
    indent: Int = 1
) {
    val attributeName = attribute.name
    val request = tagAttributeVar(repository, attribute, receiver, indent)
    append("\n")

    indent(indent)
    getter().defineIs(StringBuilder().apply {
        append(request.delegatePropertyName).append("[this, ${attributeName.quote()}]")
    })

    indent(indent)
    setter {
        append(request.delegatePropertyName).append("[this, ${attributeName.quote()}] = newValue")
    }

    emptyLine()
}

fun Appendable.facade(repository: Repository, facade: AttributeFacade) {
    clazz(Clazz(facade.className, isInterface = true, parents = facade.parents)) {
    }

    facade.declaredAttributes.filter { !isAttributeExcluded(it.name) }.forEach { attribute ->
        if (attribute.name.isLowerCase() || attribute.name.lowercase() !in facade.attributeNames) {
            attributeProperty(repository, attribute, receiver = facade.className, indent = 0)
        }
    }
}

fun Appendable.eventProperty(parent: String, attribute: AttributeInfo, shouldUnsafeCast: Boolean) {
    val type = "(web.events.Event) -> Unit"
    variable(
        receiver = parent,
        variable = Var(
            name = attribute.fieldName + "Function",
            type = type,
            varType = VarType.MUTABLE,
        )
    )
    emptyLine()

    getter().defineIs(StringBuilder().apply {
        append("throw ")
        functionCall("UnsupportedOperationException", listOf("You can't read variable ${attribute.fieldName}".quote()))
    })
    setter {
        receiverDot("consumer")
        val newValue = if (shouldUnsafeCast) {
            "newValue.unsafeCast<(Event) -> Unit>()"
        } else {
            "newValue"
        }
        functionCall(
            "onTagEvent", listOf(
                "this",
                attribute.name.quote(),
                newValue
            )
        )
    }
    emptyLine()
}

// Map event names to their specific event types for kotlin-browser 2025.x
// Package structure: web.mouse, web.keyboard, web.focus, web.input, web.touch, web.dnd, web.pointer
private val eventTypeMap = mapOf(
    // Mouse events (web.mouse)
    "onclick" to ClassName("web.mouse", "MouseEvent"),
    "ondblclick" to ClassName("web.mouse", "MouseEvent"),
    "oncontextmenu" to ClassName("web.mouse", "MouseEvent"),
    "onmousedown" to ClassName("web.mouse", "MouseEvent"),
    "onmouseup" to ClassName("web.mouse", "MouseEvent"),
    "onmouseenter" to ClassName("web.mouse", "MouseEvent"),
    "onmouseleave" to ClassName("web.mouse", "MouseEvent"),
    "onmouseover" to ClassName("web.mouse", "MouseEvent"),
    "onmouseout" to ClassName("web.mouse", "MouseEvent"),
    "onmousemove" to ClassName("web.mouse", "MouseEvent"),
    // Keyboard events (web.keyboard)
    "onkeydown" to ClassName("web.keyboard", "KeyboardEvent"),
    "onkeyup" to ClassName("web.keyboard", "KeyboardEvent"),
    "onkeypress" to ClassName("web.keyboard", "KeyboardEvent"),
    // Input events (web.input)
    "oninput" to ClassName("web.input", "InputEvent"),
    // Focus events (web.focus)
    "onfocus" to ClassName("web.focus", "FocusEvent"),
    "onblur" to ClassName("web.focus", "FocusEvent"),
    "onfocusin" to ClassName("web.focus", "FocusEvent"),
    "onfocusout" to ClassName("web.focus", "FocusEvent"),
    // Drag events (web.dnd)
    "ondrag" to ClassName("web.dnd", "DragEvent"),
    "ondragstart" to ClassName("web.dnd", "DragEvent"),
    "ondragend" to ClassName("web.dnd", "DragEvent"),
    "ondragenter" to ClassName("web.dnd", "DragEvent"),
    "ondragleave" to ClassName("web.dnd", "DragEvent"),
    "ondragover" to ClassName("web.dnd", "DragEvent"),
    "ondrop" to ClassName("web.dnd", "DragEvent"),
    // Wheel event (web.mouse)
    "onwheel" to ClassName("web.mouse", "WheelEvent"),
    // Touch events (web.touch)
    "ontouchstart" to ClassName("web.touch", "TouchEvent"),
    "ontouchend" to ClassName("web.touch", "TouchEvent"),
    "ontouchmove" to ClassName("web.touch", "TouchEvent"),
    "ontouchcancel" to ClassName("web.touch", "TouchEvent"),
    // Pointer events (web.pointer)
    "onpointerdown" to ClassName("web.pointer", "PointerEvent"),
    "onpointerup" to ClassName("web.pointer", "PointerEvent"),
    "onpointermove" to ClassName("web.pointer", "PointerEvent"),
    "onpointerenter" to ClassName("web.pointer", "PointerEvent"),
    "onpointerleave" to ClassName("web.pointer", "PointerEvent"),
    "onpointerover" to ClassName("web.pointer", "PointerEvent"),
    "onpointerout" to ClassName("web.pointer", "PointerEvent"),
    "onpointercancel" to ClassName("web.pointer", "PointerEvent"),
)

private val defaultEventType = ClassName("web.events", "Event")

fun eventProperty(parent: TypeName, attribute: AttributeInfo, shouldUnsafeCast: Boolean, useTypedEvents: Boolean = true): PropertySpec {
    // For JS: use typed events (MouseEvent, etc.) with unsafeCast
    // For WasmJS: use generic Event (no unsafeCast available for lambdas)
    val eventType = if (useTypedEvents) {
        eventTypeMap[attribute.name] ?: defaultEventType
    } else {
        defaultEventType
    }
    val propertyType = LambdaTypeName.get(
        returnType = ClassName("kotlin", "Unit"),
        parameters = listOf(ParameterSpec.unnamed(eventType)),
    )
    return PropertySpec.builder(attribute.fieldName + "Function", propertyType)
        .mutable()
        .receiver(parent)
        .getter(
            FunSpec.getterBuilder()
                .addStatement("throw UnsupportedOperationException(\"You can't read variable ${attribute.fieldName}\")")
                .build()
        )
        .setter(
            FunSpec.setterBuilder()
                .addParameter("newValue", propertyType)
                .addStatement(
                    "consumer.onTagEvent(this, %S, %L)",
                    attribute.name,
                    if (shouldUnsafeCast) {
                        "newValue.unsafeCast<(Event) -> Unit>()"
                    } else {
                        "newValue"
                    }
                )
                .build()
        )
        .build()
}
