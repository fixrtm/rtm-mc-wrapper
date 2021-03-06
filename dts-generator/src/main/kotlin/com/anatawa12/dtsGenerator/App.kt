/*
 * This Kotlin source file was generated by the Gradle 'init' task.
 */
package com.anatawa12.dtsGenerator

import org.objectweb.asm.*
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.Remapper
import java.io.File
import java.io.InputStream
import java.lang.Exception
import java.lang.reflect.Modifier
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/*
// file type
srg:<getter>
jar:<getter>
classes:<path getter>
comment:<getter>
// looping getter
zip:<getter>!<path>
get:<getter gets url>
// ending getter
file:<normal file path>
str:<string literal that return>
 */

class EndProcessArgs(val classes: ClassesManager) {
    var srgManager: SrgManager? = null
    var comment: String? = null
    var always: Boolean = false
    val only: MutableList<String> = mutableListOf()
    val exclude: MutableList<String> = mutableListOf()
    val signatures = mutableMapOf<String, String>()
    val mcpMap = mutableMapOf<String, String>()
}

class GenProcessArgs(val classes: ClassesManager) {
    val alwaysFound: MutableList<String> = mutableListOf()
    val alwaysExclude: MutableList<String> = mutableListOf()
    val elementConditions: MutableList<(TheElement) -> Boolean> = mutableListOf()
    val methodConditions: MutableList<(TheSingleMethod) -> Boolean> = mutableListOf()
    val rootPackage get() = classes.rootPackage
    var header: String? = null
    var useObjectForUnknown: Boolean = false
    fun testElement(elem: TheElement) = elementConditions.all { it(elem) }
    fun testElement(elem: TheSingleMethod) = methodConditions.all { it(elem) }
}

fun main(args: Array<String>) {
    val outputFile = args.first()

    val classes = ClassesManager()
    val genProcess = GenProcessArgs(classes)
    var endProcessArgs = EndProcessArgs(classes)

    val srgCache = mutableMapOf<String, SrgManager>()

    args@for (arg in args.asSequence().drop(1)) {
        if (arg in srgCache) {
            endProcessArgs.srgManager = srgCache[arg]
            continue@args
        }

        val (endProcess, processes, firstInput) = readArg(arg);
        var input = firstInput.toByteArray().inputStream() as InputStream

        for (process in processes) {
            input = process.process(input)
        }

        when (endProcess) {
            EndProcessType.Srg -> {
                val manager = readSrgFile(input)
                srgCache[arg] = manager
                endProcessArgs.srgManager = manager
            }
            EndProcessType.Jar -> {
                readJarFile(input, endProcessArgs)
                endProcessArgs = EndProcessArgs(classes)
            }
            EndProcessType.Classes -> {
                readClassesDir(input.reader().readText(), endProcessArgs)
                endProcessArgs = EndProcessArgs(classes)
            }
            EndProcessType.Comment -> {
                endProcessArgs.comment = input.reader().readText()
            }
            EndProcessType.Only -> {
                endProcessArgs.only += input.reader().readText()
            }
            EndProcessType.Header -> {
                genProcess.header = input.reader().readText()
            }
            EndProcessType.AlwaysFound -> {
                genProcess.alwaysFound += input.reader().readText()
            }
            EndProcessType.AlwaysExclude -> {
                genProcess.alwaysExclude += input.reader().readText()
            }
            EndProcessType.UseObjectForUnknown -> {
                genProcess.useObjectForUnknown = true
            }
            EndProcessType.Condition -> {
                val (element, method) = parseCondition(input.reader().readText())
                genProcess.elementConditions += element
                genProcess.methodConditions += method
            }
            EndProcessType.Exclude -> {
                endProcessArgs.exclude += input.reader().readText()
            }
            EndProcessType.Always -> {
                endProcessArgs.always = true
            }
            EndProcessType.Need -> {
                classes.getClass(input.reader().readText()).need = true
            }
            EndProcessType.Sig -> {
                val (k, v) = input.reader().readText().split('!', limit = 2)
                endProcessArgs.signatures[k] = v
            }
            EndProcessType.Mcp -> {
                input.reader().useLines { lines ->
                    for (line in lines.drop(1)) {
                        val (srg, mcp) = line.split(',')
                        endProcessArgs.mcpMap[srg] = mcp
                    }
                }
            }
        }
    }

    println("checking needs")
    NeedsChecker.checkNeeds(genProcess, classes)
    println("removing empty packages")
    EmptyPackageRemover.removeEmptyPackage(genProcess, classes)

    val (tag, fileName) = outputFile.split(':', limit = 2)
    when (tag) {
        "dts" -> {
            TsGen.generate(genProcess).apply {
                File(fileName).writeText(this)
            }
        }
        "jar" -> {
            JarGen.generate(genProcess, File(fileName))
        }
        "included-dts" -> {
            val (outFile, include) = fileName.split('!')
            IncludedDTsGen.generate(include, genProcess).apply { 
                File(outFile).apply { parentFile.mkdirs() }.writeText(this)
            }
        }
        else -> error("$tag is not valid export type")
    }
}

fun parseCondition(text: String): Pair<(TheElement) -> Boolean, (TheSingleMethod) -> Boolean> {
    val parts = text.split(':')
    val (pair, index) = parseConditionMain(parts, 0)
    if (index != parts.size)
        throw IllegalArgumentException("condition text contain too many ':'")
    return pair
}

fun parseConditionMain(parts: List<String>, index: Int): Pair<Pair<(TheElement) -> Boolean, (TheSingleMethod) -> Boolean>, Int> {
    when (parts[index]) {
        "comment-disallow-either" -> {
            val a = parts[index + 1]
            val b = parts[index + 2]
            fun testComment(comments: List<String>): Boolean {
                var foundA = false
                var foundB = false
                for (comment in comments) {
                    if (a in comment) foundA = true
                    if (b in comment) foundB = true
                    if (foundA && foundB) return true
                }
                return !(foundA xor foundB)
            }
            return { e: TheElement ->
                when (e) {
                    TheDuplicated -> true
                    is ThePackage -> true
                    is TheMethods -> true
                    is TheClass -> testComment(e.comments)
                    is TheField -> testComment(e.comments)
                }
            } to { method: TheSingleMethod ->
                testComment(method.comments)
            } to index + 3
        }
        "if-srg" -> {
            return { e: TheElement ->
                when (e) {
                    TheDuplicated -> true
                    is ThePackage -> true
                    is TheClass -> true
                    is TheMethods -> e.name.startsWith("func_")
                    is TheField -> e.name.startsWith("field_")
                }
            } to { method: TheSingleMethod ->
                method.name.startsWith("func_")
            } to index + 1
        }
        "not" -> {
            val (condition1, index1) = parseConditionMain(parts, index + 1)
            val (element1, method1) = condition1
            return { e: TheElement ->
                !element1(e)
            } to { method: TheSingleMethod ->
                !method1(method)
            } to index1
        }
        "and" -> {
            val (condition1, index1) = parseConditionMain(parts, index + 1)
            val (condition2, index2) = parseConditionMain(parts, index1)
            val (element1, method1) = condition1
            val (element2, method2) = condition2
            return  { e: TheElement ->
                element1(e) && element2(e)
            } to { method: TheSingleMethod ->
                method1(method) && method2(method)
            } to index2
        }
        "or" -> {
            val (condition1, index1) = parseConditionMain(parts, index + 1)
            val (condition2, index2) = parseConditionMain(parts, index1)
            val (element1, method1) = condition1
            val (element2, method2) = condition2
            return  { e: TheElement ->
                element1(e) || element2(e)
            } to { method: TheSingleMethod ->
                method1(method) || method2(method)
            } to index2
        }
        "xor" -> {
            val (condition1, index1) = parseConditionMain(parts, index + 1)
            val (condition2, index2) = parseConditionMain(parts, index1)
            val (element1, method1) = condition1
            val (element2, method2) = condition2
            return  { e: TheElement ->
                element1(e) xor element2(e)
            } to { method: TheSingleMethod ->
                method1(method) xor method2(method)
            } to index2
        }
        "xnor" -> {
            val (condition1, index1) = parseConditionMain(parts, index + 1)
            val (condition2, index2) = parseConditionMain(parts, index1)
            val (element1, method1) = condition1
            val (element2, method2) = condition2
            return  { e: TheElement ->
                !(element1(e) xor element2(e))
            } to { method: TheSingleMethod ->
                !(method1(method) xor method2(method))
            } to index2
        }
        else -> error("invalid: ${parts[0]}")
    }
}

object NeedsChecker {
    private fun <T> MutableIterable<T>.removeFirst() = iterator().run { next().also { remove() } }

    fun checkNeeds(args: GenProcessArgs, classes: ClassesManager) {
        val willCheck = getAllNeeds(classes)
        val _checked = mutableSetOf<TheClass>()
        val checked = _checked as Set<TheClass>
        while (willCheck.isNotEmpty()) {
            val theClass = willCheck.removeFirst()
            theClass.need = true
            _checked += theClass

            setNeedForOutersAnd(willCheck, checked, theClass)

            val signature = theClass.signature
            if (!theClass.gotClass) {
                // nop
            } else if (signature == null) {
                for (s in (listOfNotNull(theClass.superClass) + theClass.interfaces)) {
                    add(willCheck, checked, classes.getClass(s))
                }
            } else {
                addType(willCheck, checked, classes, signature.superClass)
                for (superType in signature.superInterfaces) {
                    addType(willCheck, checked, classes, superType)
                }
                addTypeParms(willCheck, checked, classes, signature.params)
            }

            children@for (element in theClass.children.values) {
                when (element) {
                    is ThePackage -> error("package is not allowed for child of ckass")
                    is TheClass -> willCheck += element
                    is TheMethods -> {
                        for (method in element.singles.values) {
                            if (!GenUtil.canVisitMethod(args, theClass, method)) continue@children
                            addTypeParms(willCheck, checked, classes, method.signature.typeParams)
                            for (param in method.signature.params) {
                                addType(willCheck, checked, classes, param)
                            }
                            method.signature.result?.let { addType(willCheck, checked, classes, it) }
                        }
                    }
                    is TheField -> {
                        if (!GenUtil.canVisitField(args, theClass, element)) continue@children
                        addType(willCheck, checked, classes, element.signature)
                    }
                }
            }
        }
    }

    private fun addTypeParms(willCheck: MutableSet<TheClass>, checked: Set<TheClass>, classes: ClassesManager, params: List<TypeParam>) {
        for (param in params) {
            for (superType in param.superTypes) {
                addType(willCheck, checked, classes, superType)
            }
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun addType(willCheck: MutableSet<TheClass>, checked: Set<TheClass>, classes: ClassesManager, type: JavaTypeSignature) {
        val willSearchs = mutableListOf(type)
        while (willSearchs.isNotEmpty()) {
            when (val t = willSearchs.removeLast()) {
                is BaseType -> { /* nop */ }
                is ClassTypeSignature -> {
                    add(willCheck, checked, classes.getClass(t.name))
                    for (arg in t.args) {
                        if (arg.type != null) willSearchs += arg.type
                    }
                }
                is TypeVariable -> { /* nop */ }
                is ArrayTypeSignature -> {
                    willSearchs += t.element
                }
            }
        }
    }

    private fun setNeedForOutersAnd(willCheck: MutableSet<TheClass>, checked: Set<TheClass>, theClass: TheClass) {
        var outest = theClass
        while (outest.outerClass != null) outest = outest.outerClass!!
        val children = mutableSetOf(outest)
        while (children.isNotEmpty()) {
            val child = children.removeFirst()

            child.need = true

            add(willCheck, checked, child)
            for (element in child.children.values) {
                when (element) {
                    is ThePackage -> error("package is not allowed for child of ckass")
                    is TheClass -> children += element
                    is TheMethods -> {}
                    is TheField -> {}
                }
            }
        }
    }

    private fun add(willCheck: MutableSet<TheClass>, checked: Set<TheClass>, theClass: TheClass) {
        if (theClass in willCheck) return
        if (theClass in checked) return
        willCheck += theClass
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun getAllNeeds(classes: ClassesManager): MutableSet<TheClass> {
        val packages = mutableListOf(classes.rootPackage)
        val needs = mutableSetOf<TheClass>()
        while (packages.isNotEmpty()) {
            val pkg = packages.removeLast()
            for (value in pkg.children.values) {
                when (value) {
                    is ThePackage -> packages += value
                    is TheClass -> {
                        if (value.need)
                            needs += value
                    }
                    is TheMethods -> error("method is not allowed for child of package")
                    is TheField -> error("method is not allowed for child of package")
                }
            }
        }
        
        /** primitives **/

        for (primitiveClass in primitiveClasses) {
            classes.getClass(primitiveClass).need = true
        }

        return needs
    }

    private val primitiveClasses = mutableListOf(
            "java.lang.Byte",
            "java.lang.Character",
            "java.lang.Double",
            "java.lang.Float",
            "java.lang.Integer",
            "java.lang.Long",
            "java.lang.Short",
            "java.lang.Boolean"
    )
}

fun readArg(arg: String): Triple<EndProcessType, List<InputGetterProcess>, String> {
    try {
        var (tag, path) = arg.split(":", limit = 2)
        val endProcess: EndProcessType

        when (tag) {
            "srg" -> endProcess = EndProcessType.Srg
            "jar" -> endProcess = EndProcessType.Jar
            "classes" -> endProcess = EndProcessType.Classes
            "comment" -> endProcess = EndProcessType.Comment
            "only" -> endProcess = EndProcessType.Only
            "header" -> endProcess = EndProcessType.Header
            "always-found" -> endProcess = EndProcessType.AlwaysFound
            "always-exclude" -> endProcess = EndProcessType.AlwaysExclude
            "use-object-for-unknown" -> return Triple(EndProcessType.UseObjectForUnknown, emptyList(), "")
            "condition" -> return Triple(EndProcessType.Condition, emptyList(), path)
            "exclude" -> endProcess = EndProcessType.Exclude
            "always" -> return Triple(EndProcessType.Always, emptyList(), "")
            "need" -> endProcess = EndProcessType.Need
            "sig" -> endProcess = EndProcessType.Sig
            "mcp" -> endProcess = EndProcessType.Mcp
            else -> error("invalid end process type: $tag")
        }

        run {
            val (a, b) = path.split(":", limit = 2)
            tag = a
            path = b
        }

        val processes = mutableListOf<InputGetterProcess>()

        while (true) {
            when (tag) {
                "zip" -> {
                    val (getter, zipPath) = path.split('!', limit = 2)
                    processes += ZipGetterProcess(zipPath, getter)
                    val (a, b) = getter.split(":", limit = 2)
                    tag = a
                    path = b
                }
                "get" -> {
                    processes += GetGetterProcess
                    val (a, b) = path.split(":", limit = 2)
                    tag = a
                    path = b
                }
                "file" -> {
                    processes += FileGetterProcess
                    val (a, b) = path.split(":", limit = 2)
                    tag = a
                    path = b
                }
                "str" -> {
                    processes.reverse()
                    return Triple(endProcess, processes, path)
                }
                "jvm-home" -> {
                    processes.reverse()
                    val jvmRt = System.getProperty("java.home") + "/" + path
                    return Triple(endProcess, processes, jvmRt)
                }
                else -> error("invalid getter tag: $tag")
            }
        }
    } catch (e: IndexOutOfBoundsException) {
        throw Exception("reading '$arg'", e)
    }
}

enum class EndProcessType {
    Srg,
    Jar,
    Classes,
    Comment,
    Only,
    Header,
    AlwaysFound,
    AlwaysExclude,
    UseObjectForUnknown,
    Condition,
    Exclude,
    Always,
    Need,
    Sig,
    Mcp,
}

interface InputGetterProcess {
    fun process(stream: InputStream): InputStream
}

class ZipGetterProcess(val zipPath: String, val getter: String): InputGetterProcess {
    override fun process(stream: InputStream): InputStream {
        val zis = ZipInputStream(stream)
        println("searching in zip: $zipPath")
        var entry: ZipEntry? = null
        while (true) {
            entry = zis.nextEntry ?: break
            if (entry.name == zipPath) break
        }
        if (entry?.name != zipPath)
            throw IllegalStateException("$zipPath not found in zip $getter")
        return zis
    }
}

object GetGetterProcess: InputGetterProcess {
    private val cache = mutableMapOf<String, ByteArray>()
    override fun process(stream: InputStream): InputStream {
        val url = stream.reader().readText()
        if (url in cache) {
            println("using cache for url: $url")
            return cache[url]!!.inputStream()
        }
        println("getting url: $url")
        val bytes = URL(url).openStream().readBytes()
        cache[url] = bytes
        return bytes.inputStream()
    }
}

object FileGetterProcess: InputGetterProcess {
    override fun process(stream: InputStream): InputStream {
        val path = stream.reader().readText()
        println("getting file: $path")
        return File(path).inputStream()
    }
}

fun readSrgFile(stream: InputStream): SrgManager = stream.bufferedReader().use { reader ->
    val manager = SrgManager();
    for (line in reader.lineSequence()) {
        val parts = line.split(" ")
        when (parts.first()) {
            "CL:" -> {
                manager.addClass(parts[1], parts[2])
            }
            "FD:" -> {
                val classAndName = parts[2]
                val lastSlash = classAndName.lastIndexOf("/")
                manager.addField(parts[1],
                        classAndName.substring(0, lastSlash),
                        classAndName.substring(lastSlash + 1))
            }
            "MD:" -> {
                val classAndName = parts[3]
                val lastSlash = classAndName.lastIndexOf("/")
                manager.addMethod(parts[1] + parts[2],
                        classAndName.substring(0, lastSlash),
                        classAndName.substring(lastSlash + 1),
                        parts[4])
            }
        }
    }

    return manager
}

val anonymousRegex = """\$[0-9]""".toRegex()
fun readJarFile(stream: InputStream, args: EndProcessArgs) {
    println("searching class files in jar")
    ZipInputStream(stream).use { zis ->
        while (true) {
            val entry = zis.nextEntry ?: break
            if (!entry.name.endsWith(".class")) continue
            if (entry.name.endsWith("package-info.class")) continue
            if (entry.name.endsWith("module-info.class")) continue
            if (entry.name.contains(anonymousRegex)) continue
            readClassFile(zis, args)
        }
    }
}

fun readClassesDir(path: String, args: EndProcessArgs) {
    for (file in File(path).walkBottomUp()) {
        if (!file.name.endsWith(".class")) continue
        if (file.name.endsWith("package-info.class")) continue
        if (file.name.endsWith("module-info.class")) continue
        if (file.name.contains(anonymousRegex)) continue
        println("reading class file: $file")
        readClassFile(file.inputStream(), args)
    }
}

fun readClassFile(stream: InputStream, args: EndProcessArgs) {
    val reader = ClassReader(stream.readBytes())
    val realName = args.srgManager?.classes?.get(reader.className) ?: reader.className
    if (args.only.isNotEmpty() && args.only.all { !realName.startsWith(it) }) return
    if (args.exclude.any { realName.startsWith(it) }) return
    var visitor: ClassVisitor = object : ClassVisitor(Opcodes.ASM6) {
        lateinit var theClass: TheClass
        lateinit var name: String
        override fun visit(version: Int, access: Int, name: String, signature: String?, superName: String?, interfaces: Array<out String>?) {
            this.name = name
            theClass = args.classes.getClass(name)
            theClass.gotClass = true
            if (args.always) theClass.need = true
            if (theClass.accessExternally == -1)
                theClass.accessExternally = access
            theClass.superClass = superName
            theClass.interfaces = interfaces.orEmpty()
            val signature = args.signatures[name] ?: signature
            if (signature != null) 
                theClass.signature = SigReader.current.classSignature(signature, name)
            if (args.comment != null) theClass.comments.add(args.comment!!)
            super.visit(version, access, name, signature, superName, interfaces)
        }

        override fun visitMethod(access: Int, name: String, desc: String, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
            if (access.and(Opcodes.ACC_SYNTHETIC) != 0) return null
            theClass.getMethod(name, desc)?.also {
                val signature = args.signatures["${this.name}:$name$desc"] ?: signature
                if (it.access != -1) {
                    it.access = mergeAccess(it.access, access)
                    //exits
                    addComment(it.comments, args, name, buildString {
                        if (signature != null) {
                            appendln("signature: $signature")
                        }
                        append("access: ${access.toString(16).padStart(4, '0')}")
                    })
                } else {
                    if (signature != null)
                        it.signature = SigReader.current.methodDesc(signature, "${this.name}/$name$desc")
                    addComment(it.comments, args, name)
                    it.access = access
                }
            }
            return null
        }

        private fun mergeAccess(access: Int, access1: Int): Int {
            var result = access
            if (Modifier.isAbstract(access) xor Modifier.isAbstract(access1))
                result = result and Opcodes.ACC_ABSTRACT.inv()
            if (Modifier.isFinal(access) xor Modifier.isFinal(access1))
                result = result and Opcodes.ACC_FINAL.inv()
            return result
        }

        override fun visitField(access: Int, name: String, desc: String, signature: String?, value: Any?): FieldVisitor? {
            if (access.and(Opcodes.ACC_SYNTHETIC) != 0) return null
            val signature = args.signatures["${this.name}:$name"]  ?: signature
            theClass.getField(name, desc)?.also {
                if (signature != null) 
                    it.signature = SigReader.current.fieldDesc(signature, "${this.name}/$name:$desc")
                addComment(it.comments, args, name)
                it.access = access
            }
            return null
        }

        private fun addComment(comments: MutableList<String>, args: EndProcessArgs, name: String, sigInfo: String? = null) {
            val mcp = args.mcpMap[name]
            val comment = args.comment
            if (mcp == null && comment == null) return
            var commentReal: String
            if (mcp == null) {
                commentReal = "$comment"
            } else if (comment == null) {
                commentReal = "mcp: $mcp"
            } else {
                commentReal = "$comment\nmcp: $mcp"
            }
            if (sigInfo != null) {
                commentReal += "\nsiginfo:\n${sigInfo.lineSequence().map { "  $it" }.joinToString("\n")}"
            }
            comments += commentReal
        }

        override fun visitInnerClass(name: String, outerName: String?, innerName: String?, access: Int) {
            if (outerName != this.name) return
            if (!name.startsWith(outerName)) return
            if (name.contains(anonymousRegex)) return
            args.classes.getClass(name).also {
                if (args.always) theClass.need = true
                it.accessExternally = access
            }
            super.visitInnerClass(name, outerName, innerName, access)
        }
    }
    args.srgManager?.let { srg ->
        val remapper = object : Remapper() {
            override fun map(typeName: String): String {
                return srg.classes[typeName] ?: typeName
            }

            override fun mapMethodName(owner: String, name: String, desc: String): String {
                return srg.methods["$owner/$name$desc"]?.name ?: name
            }

            override fun mapFieldName(owner: String, name: String, desc: String): String {
                return srg.fields["$owner/$name"]?.name ?: name
            }
        }
        visitor = ClassRemapper(visitor, remapper)
    }
    
    reader.accept(visitor, ClassReader.SKIP_CODE)
}
