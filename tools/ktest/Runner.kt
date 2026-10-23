// Finds every @kotlin.test.Test method in a compiled-classes directory and runs it.
// Usage: java -cp <classes>:<deps> ktest.RunnerKt <classes-dir> [name-filter]
package ktest

import java.io.File
import java.lang.reflect.InvocationTargetException
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val root = File(args[0])
    val filter = args.getOrNull(1)
    val testAnno = Class.forName("kotlin.test.Test").asSubclass(Annotation::class.java)
    var passed = 0
    var failed = 0
    val classNames = root.walkTopDown()
        .filter { it.isFile && it.name.endsWith(".class") && !it.name.contains('$') }
        .map { it.relativeTo(root).path.removeSuffix(".class").replace(File.separatorChar, '.') }
        .filter { !it.startsWith("kotlin.") && !it.startsWith("ktest.") }
        .sorted()
        .toList()
    for (name in classNames) {
        val cls = Class.forName(name)
        val tests = cls.declaredMethods.filter { it.isAnnotationPresent(testAnno) }.sortedBy { it.name }
        for (m in tests) {
            val label = "${cls.simpleName}.${m.name}"
            if (filter != null && !label.contains(filter)) continue
            try {
                m.invoke(cls.getDeclaredConstructor().newInstance())
                passed++
                println("PASS $label")
            } catch (e: InvocationTargetException) {
                failed++
                val cause = e.targetException
                println("FAIL $label: ${cause.message ?: cause}")
                if (cause !is AssertionError) cause.printStackTrace(System.out)
            }
        }
    }
    println("$passed passed, $failed failed")
    if (failed > 0 || passed == 0) exitProcess(1)
}
