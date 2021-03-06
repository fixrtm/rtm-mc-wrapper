package com.anatawa12.dtsGenerator

import org.objectweb.asm.Opcodes


object TsGen {
    fun generate(args: GenProcessArgs) = buildSrc {
        args.header?.let { header ->
            append(header)
            appendln()
        }
        appendln("type t_unknown = unknown")
        appendln("type t_byte = number")
        appendln("type t_char = number")
        appendln("type t_double = number")
        appendln("type t_float = number")
        appendln("type t_int = number")
        appendln("type t_long = number")
        appendln("type t_short = number")
        appendln("type t_boolean = boolean")
        appendln("type t_String = String")
        appendln("type t_array<T> = Array<T>")
        appendln()
        append("declare ")
        generateClassesInPackage(args, "Packages", args.classes.rootPackage, this)
        appendln()
    }

    private val extraTypeMapping = mutableMapOf(
            "java/lang/Object" to "unknown",
            "java/lang/String" to "t_String",
            "java/lang/Byte" to "t_byte",
            "java/lang/Character" to "t_char",
            "java/lang/Double" to "t_double",
            "java/lang/Float" to "t_float",
            "java/lang/Integer" to "t_int",
            "java/lang/Long" to "t_long",
            "java/lang/Short" to "t_short",
            "java/lang/Boolean" to "t_boolean"
    )

    private fun generateClassesInPackage(args: GenProcessArgs, packageFQN: String, thePackage: ThePackage, builder: SrcBuilder): SrcBuilder = builder.apply {
        val children = thePackage.children.entries
                .filter { GenUtil.elementFilter(it.value) }
                .sortedWith(compareBy<Map.Entry<String, TheElement>> { it.value.type }
                        .thenBy { it.key })

        if (children.size == 1) {
            val (name, child) = children.single()
            if (child is ThePackage) {
                generateClassesInPackage(args, "$packageFQN.$name", child, builder)
                return@apply
            }
        }
        appendln("namespace $packageFQN {")
        indent()
        children@for ((name, child) in children) {
            when (child) {
                is ThePackage -> {
                    generateClassesInPackage(args, name, child, builder)
                }
                is TheClass -> {
                    if (child.accessExternally.and(Opcodes.ACC_PUBLIC) == 0) continue@children
                    generateClass(args, child, this)
                }
                is TheMethods -> error("method cannot be child of package")
                is TheField -> error("field cannot be child of package")
                TheDuplicated -> {
                    // nop
                }
            }
        }
        outdent()
        appendln("}")
    }

    private fun generateClass(args: GenProcessArgs, theClass: TheClass, builder: SrcBuilder) = builder.apply {
        val children = theClass.children.entries
                .sortedWith(compareBy<Map.Entry<String, TheElement>> { it.value.type }
                        //.thenComparator { a, b ->  }
                        .thenBy { it.key })
        val typeParams = theClass.signature?.params.orEmpty()
        val simpleName = theClass.name.substringAfterLast('/').substringAfterLast('.').substringAfterLast('$')

        var superClass: ClassTypeSignature?

        if (theClass.accessExternallyChecked.and(Opcodes.ACC_INTERFACE) != 0 
                && children.size == 1) {
            val child = children.single().value
            if (child is TheMethods) {
                if (child.singles.size == 1) {
                    // Single Abstract Method interface.
                    val method = child.singles.values.single()
                    append("type ").append(simpleName)
                            .append(" = {")
                            .append(methodDesc(method.signature, args))
                            .appendln("};")
                    return@apply
                }
            }
        }

        // constructor class
        generateComment(theClass.comments, this)
        if (theClass.accessExternallyChecked.and(Opcodes.ACC_INTERFACE) == 0) {
            append("class ")
        } else {
            append("interface ")
        }
        append(simpleName)
        typeParams(this, args, typeParams)
        if (theClass.signature != null) {
            val sig = theClass.signature!!
            superClass = sig.superClass
        } else {
            if (theClass.superClass != null) {
                superClass = ClassTypeSignature(theClass.superClass!!, emptyList())
            } else {
                superClass = null
            }
        }
        if (superClass?.name == "java/lang/Object")
            superClass = null
        if (superClass != null) { 
            append(" extends ")
            append(tsValItfOrPrimitive(superClass, args, true))
        }

        appendln(" {")
        indent()
        var first = true
        children@for ((name, child) in children) {
            when (child) {
                is ThePackage -> error("package cannot be child of class")
                is TheClass -> System.err.println("warn: inner class not supported: $child")
                is TheMethods -> {
                    for ((_, method) in child.singles) {
                        if (!GenUtil.canVisitMethod(args, theClass, method)) continue

                        if (method.name == "<init>") {
                            if (theClass.accessExternallyChecked.and(Opcodes.ACC_ABSTRACT) != 0) continue
                            if (!first) appendln()
                            first = false
                            generateComment(method.comments, this)
                            appendln("constructor${ctorDesc(method.signature, args)}")
                            continue
                        }
                        if (!first) appendln()
                        first = false

                        if (method.name.length > 3) {
                            val propertyName = method.name[3].toLowerCase() + method.name.substring(4)
                            if (method.name.startsWith("set")
                                    && method.signature.params.size == 1) {
                                // setter
                                appendln("set $propertyName${ctorDesc(method.signature, args)}")
                                continue
                            } else if (method.name.startsWith("get") 
                                    && method.signature.params.size == 0) {
                                // getter
                                appendln("get $propertyName${methodDesc(method.signature, args)}")
                                continue
                            }
                        }

                        if (method.accessChecked.and(Opcodes.ACC_STATIC) != 0) {
                            append("static ")
                        }
                        generateComment(method.comments, this)
                        appendln("$name${methodDesc(method.signature, args)}")
                    }
                }
                is TheField -> {
                    if (!GenUtil.canVisitField(args, theClass, child)) continue@children

                    if (!first) appendln()
                    first = false
                    if (child.accessChecked.and(Opcodes.ACC_STATIC) != 0) {
                        append("static ")
                    }

                    generateComment(child.comments, this)
                    appendln("$name: ${tsValItfOrPrimitive(child.signature, args)}")
                }
                TheDuplicated -> {
                    // nop
                }
            }
        }
        outdent()
        appendln("}")
        appendln()
    }

    private fun tsValItfOrPrimitive(type: JavaTypeSignature, args: GenProcessArgs, superType: Boolean = false): String = when (type) {
        is BaseType -> when (type.type) {
            BaseType.Kind.Byte -> "t_byte"
            BaseType.Kind.Char -> "t_char"
            BaseType.Kind.Double -> "t_double"
            BaseType.Kind.Float -> "t_float"
            BaseType.Kind.Int -> "t_int"
            BaseType.Kind.Long -> "t_long"
            BaseType.Kind.Short -> "t_short"
            BaseType.Kind.Boolean -> "t_boolean"
        }
        is ClassTypeSignature -> buildString {
            if (!superType && type.name in extraTypeMapping) {
                append(extraTypeMapping[type.name])
            } else if (!GenUtil.canPoetClass(args, type.name, true)) {
                append("t_unknown /* ${type.name.substringAfterLast('/')} */")
            } else {
                append("Packages." + type.name.replace('/', '.'))
                if (type.args.isNotEmpty()) {
                    append('<')
                    type.args.joinTo(this) {
                        if (it.type == null) "any"
                        else tsValItfOrPrimitive(it.type, args, true)
                    }
                    append('>')
                }
            }
        }
        is TypeVariable -> type.name
        is ArrayTypeSignature -> "t_array<${tsValItfOrPrimitive(type.element, args, superType)}>"
    }

    private fun typeParams(builder: SrcBuilder, args: GenProcessArgs, typeParams: List<TypeParam>) = with(builder) {
        if (typeParams.isNotEmpty()) {
            val containList = mutableSetOf<String>()
            var id = 0
            val namePair = typeParams.asReversed()
                    .map {
                        if (it.name in containList) {
                            it to "${it.name}_${id++}"
                        } else {
                            containList.add(it.name)
                            it to it.name
                        }
                    }
                    .asReversed()
            append('<')
            var first = true
            for ((typeParam, name) in namePair) {
                if (!first) append(", ")
                first = false
                append(name)

                if (typeParam.superTypes.isNotEmpty()) {
                    append(" extends ")
                    var first1 = true
                    for (superType in typeParam.superTypes) {
                        if (!first1) append(" & ")
                        first1 = false
                        append(tsValItfOrPrimitive(superType, args))
                    }
                }
                append(" = any")
            }
            append('>')
        }
    }

    private fun methodDesc(signature: MethodSignature, args: GenProcessArgs) = buildSrc {
        typeParams(this, args, signature.typeParams)
        append('(')
        var first = true
        for ((i, typeSignature) in signature.params.withIndex()) {
            if (!first) append(", ")
            first = false
            append("par$i: ${tsValItfOrPrimitive(typeSignature, args)}")
        }
        append("): ")
        if (signature.result == null)
            append("void")
        else
            append(tsValItfOrPrimitive(signature.result, args))
    }

    private fun ctorDesc(signature: MethodSignature, args: GenProcessArgs) = buildSrc {
        append('(')
        var first = true
        for ((i, typeSignature) in signature.params.asSequence().drop(0).withIndex()) {
            if (!first) append(", ")
            first = false
            append("par$i: ${tsValItfOrPrimitive(typeSignature, args)}")
        }
        append(")")
    }

    private fun generateComment(comments: MutableList<String>, builder: SrcBuilder) = builder.apply {
        if (comments.isEmpty()) return@apply
        appendln("/**")
        var first = true
        for (comment in comments) {
            if (!first) appendln(" * ")
            first = false
            for (line in comment.lineSequence()) {
                appendln(" * $line")
            }
        }
        appendln(" */")
    }
}
