package dev.rhovas.interpreter.environment

import dev.rhovas.interpreter.library.Library
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class TypeTests {

    val ANY = Type.Base("Any", listOf(), listOf(), Scope.Definition(null)).reference
    val NUMBER = Type.Base("Number", listOf(), listOf(ANY), Scope.Definition(null)).reference
    val INTEGER = Type.Base("Integer", listOf(), listOf(NUMBER), Scope.Definition(null)).reference
    val COLLECTION = Type.Base("Collection", listOf(Type.Generic("T", ANY)), listOf(ANY), Scope.Definition(null)).reference
    val LIST = Type.Base("List", listOf(Type.Generic("T", ANY)), listOf(COLLECTION), Scope.Definition(null)).reference
    val DYNAMIC = Type.Base("Dynamic", listOf(), listOf(), Scope.Definition(null)).reference

    @ParameterizedTest(name = "{0}")
    @MethodSource
    fun testGetFunction(test: String, scope: Scope.Definition, name: String, arguments: List<Type>, expected: Type?) {
        Assertions.assertEquals(expected, scope.functions[name, arguments]?.returns)
    }

    fun testGetFunction(): Stream<Arguments> {
        val scope = Scope.Definition(null)
        scope.functions.define(Function.Definition(Function.Declaration("number", listOf(), listOf(Variable.Declaration("number", NUMBER, false)), ANY, listOf())))
        scope.functions.define(Function.Definition(Function.Declaration("get", listOf(Type.Generic("T", ANY)), listOf(Variable.Declaration("list", Type.Reference(LIST.base, listOf(Type.Generic("T", ANY))), false), Variable.Declaration("index", INTEGER, false)), Type.Generic("T", ANY), listOf())))
        scope.functions.define(Function.Definition(Function.Declaration("set", listOf(Type.Generic("T", ANY)), listOf(Variable.Declaration("list", Type.Reference(LIST.base, listOf(Type.Generic("T", ANY))), false), Variable.Declaration("index", INTEGER, false), Variable.Declaration("value", Type.Generic("T", ANY), false)), Type.Generic("T", ANY), listOf())))
        scope.functions.define(Function.Definition(Function.Declaration("set2", listOf(Type.Generic("T", ANY)), listOf(Variable.Declaration("value", Type.Generic("T", ANY), false), Variable.Declaration("index", INTEGER, false), Variable.Declaration("list", Type.Reference(LIST.base, listOf(Type.Generic("T", ANY))), false)), Type.Generic("T", ANY), listOf())))
        return Stream.of(
            Arguments.of("Equal", scope, "number", listOf(NUMBER), ANY),
            Arguments.of("Subtype", scope, "number", listOf(INTEGER), ANY),
            Arguments.of("Supertype", scope, "number", listOf(ANY), null),
            Arguments.of("Generic Unbound", scope, "get", listOf(LIST, INTEGER), Type.Generic("T", ANY)),
            Arguments.of("Generic Bound", scope, "get", listOf(Type.Reference(LIST.base, listOf(INTEGER)), INTEGER), INTEGER),
            Arguments.of("Generic Multiple", scope, "set", listOf(Type.Reference(LIST.base, listOf(INTEGER)), INTEGER, INTEGER), INTEGER),
            Arguments.of("Generic Multiple Primitive Subtype First", scope, "set2", listOf(INTEGER, INTEGER, Type.Reference(LIST.base, listOf(NUMBER))), NUMBER),
            Arguments.of("Generic Multiple Primitive Subtype Second", scope, "set2", listOf(NUMBER, INTEGER, Type.Reference(LIST.base, listOf(INTEGER))), null),
            Arguments.of("Generic Multiple Generic Subtype First", scope, "set", listOf(Type.Reference(LIST.base, listOf(INTEGER)), INTEGER, NUMBER), null),
            Arguments.of("Generic Multiple Generic Subtype Second", scope, "set", listOf(Type.Reference(LIST.base, listOf(NUMBER)), INTEGER, INTEGER), NUMBER),
            Arguments.of("Generic Multiple Dynamic First", scope, "set", listOf(Type.Reference(LIST.base, listOf(DYNAMIC)), INTEGER, INTEGER), DYNAMIC),
            Arguments.of("Generic Multiple Dynamic Second", scope, "set", listOf(Type.Reference(LIST.base, listOf(INTEGER)), INTEGER, DYNAMIC), DYNAMIC),
            Arguments.of("Generic Multiple Mismatch", scope, "set", listOf(Type.Reference(LIST.base, listOf(INTEGER)), INTEGER, LIST), null),
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    fun testGetMethod(test: String, type: Type, name: String, arguments: List<Type>, expected: Type?) {
        Assertions.assertEquals(expected, type.methods[name, arguments]?.returns)
    }

    fun testGetMethod(): Stream<Arguments> {
        NUMBER.base.scope.functions.define(Function.Definition(Function.Declaration("<=>", listOf(), listOf(Variable.Declaration("this", NUMBER, false), Variable.Declaration("other", NUMBER, false)), INTEGER, listOf())).also {
            it.implementation = { Object(Library.TYPES["Void"]!!, Unit) }
        })
        LIST.base.scope.functions.define(Function.Definition(Function.Declaration("get", listOf(Type.Generic("T", ANY)), listOf(Variable.Declaration("this", LIST, false), Variable.Declaration("index", INTEGER, false)), Type.Generic("T", ANY), listOf())).also {
            it.implementation = { Object(Library.TYPES["Void"]!!, Unit) }
        })
        return Stream.of(
            Arguments.of("Equal", NUMBER, "<=>", listOf(NUMBER), INTEGER),
            Arguments.of("Subtype", NUMBER, "<=>", listOf(INTEGER), INTEGER),
            Arguments.of("Supertype", NUMBER, "<=>", listOf(ANY), null),
            Arguments.of("Dynamic", DYNAMIC, "undefined", listOf(ANY), DYNAMIC),
            Arguments.of("Generic Unbound", LIST, "get", listOf(INTEGER), Type.Generic("T", ANY)),
            Arguments.of("Generic Bound", LIST.bind(mapOf("T" to INTEGER)), "get", listOf(INTEGER), INTEGER),
        )
    }

    @Nested
    inner class SubtypeTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource
        fun testBase(name: String, first: Type, second: Type, expected: Boolean) {
            Assertions.assertEquals(expected, first.isSubtypeOf(second))
        }

        fun testBase(): Stream<Arguments> {
            return Stream.of(
                Arguments.of("Equal", NUMBER, NUMBER, true),
                Arguments.of("Subtype", INTEGER, NUMBER, true),
                Arguments.of("Supertype", NUMBER, INTEGER, false),
                Arguments.of("Grandchild", INTEGER, ANY, true),
                Arguments.of("Dynamic Subtype", DYNAMIC, NUMBER, true),
                Arguments.of("Dynamic Supertype", NUMBER, DYNAMIC, true),
            )
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource
        fun testBoundGeneric(name: String, first: Type, second: Type, expected: Boolean) {
            Assertions.assertEquals(expected, first.isSubtypeOf(second))
        }

        fun testBoundGeneric(): Stream<Arguments> {
            return Stream.of(
                Arguments.of("Equal", Type.Reference(LIST.base, listOf(NUMBER)), Type.Reference(LIST.base, listOf(NUMBER)), true),
                Arguments.of("Base Subtype", Type.Reference(LIST.base, listOf(NUMBER)), Type.Reference(COLLECTION.base, listOf(NUMBER)), true),
                Arguments.of("Base Supertype", Type.Reference(COLLECTION.base, listOf(NUMBER)), Type.Reference(LIST.base, listOf(NUMBER)), false),
                Arguments.of("Base Grandchild", Type.Reference(LIST.base, listOf(NUMBER)), ANY, true),
                Arguments.of("Generic Subtype", Type.Reference(LIST.base, listOf(INTEGER)), Type.Reference(LIST.base, listOf(NUMBER)), false),
                Arguments.of("Generic Supertype", Type.Reference(LIST.base, listOf(NUMBER)), Type.Reference(LIST.base, listOf(INTEGER)), false),
                Arguments.of("Generic Dynamic Subtype", Type.Reference(LIST.base, listOf(DYNAMIC)), Type.Reference(LIST.base, listOf(NUMBER)), true),
                Arguments.of("Generic Dynamic Supertype", Type.Reference(LIST.base, listOf(NUMBER)), Type.Reference(LIST.base, listOf(DYNAMIC)), true),
            )
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource
        fun testUnboundGeneric(name: String, first: Type, second: Type, expected: Boolean) {
            Assertions.assertEquals(expected, first.isSubtypeOf(second))
        }

        fun testUnboundGeneric(): Stream<Arguments> {
            return Stream.of(
                Arguments.of("Equal", LIST, LIST, true),
                Arguments.of("Subtype", LIST, COLLECTION, true),
                Arguments.of("Supertype", COLLECTION, LIST, false),
                Arguments.of("Grandchild", LIST, ANY, true),
                Arguments.of("Generic Dynamic Subtype", Type.Reference(LIST.base, listOf(DYNAMIC)), LIST, true),
                Arguments.of("Generic Dynamic Supertype", LIST, Type.Reference(LIST.base, listOf(DYNAMIC)), true),
            )
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource
        fun testRawGeneric(name: String, first: Type, second: Type, expected: Boolean) {
            Assertions.assertEquals(expected, first.isSubtypeOf(second))
        }

        fun testRawGeneric(): Stream<Arguments> {
            return Stream.of(
                Arguments.of("Equal", Type.Generic("T", NUMBER), Type.Generic("T", NUMBER), true),
                Arguments.of("Unequal", Type.Generic("T", NUMBER), Type.Generic("R", NUMBER), false),
                Arguments.of("Bound Subtype", Type.Generic("T", INTEGER), NUMBER, true),
                Arguments.of("Bound Supertype", Type.Generic("T", NUMBER), INTEGER, false),
                Arguments.of("Bound Dynamic Subtype", Type.Generic("T", DYNAMIC), NUMBER, true),
                Arguments.of("Bound Dynamic Supertype", Type.Generic("T", NUMBER), DYNAMIC, true),
            )
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource
        fun testVariantGeneric(name: String, first: Type, second: Type, expected: Boolean) {
            Assertions.assertEquals(expected, first.isSubtypeOf(second))
        }

        fun testVariantGeneric(): Stream<Arguments> {
            return Stream.of(
                Arguments.of("Covariant Subtype", INTEGER, Type.Variant(null, NUMBER), true),
                Arguments.of("Covariant Supertype", ANY, Type.Variant(null, NUMBER), false),
                Arguments.of("Contravariant Subtype", INTEGER, Type.Variant(NUMBER, ANY), false),
                Arguments.of("Contravariant Supertype", ANY, Type.Variant(NUMBER, ANY), true),
            )
        }

    }

}
