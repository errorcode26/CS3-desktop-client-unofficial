package com.fasterxml.jackson.databind.introspect

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.databind.AnnotationIntrospector
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.PropertyName
import com.fasterxml.jackson.databind.cfg.MapperConfig

/**
 * Universal protection wrapper for Jackson's AnnotationIntrospector on desktop.
 *
 * Placed in `com.fasterxml.jackson.databind.introspect` to access package-private [PotentialCreator].
 *
 * When dex2jar converts Android plugin DEX bytecode to JVM JARs, local/synthetic data classes
 * declared inside functions (e.g. `fun getMainPage() { data class Foo(...) }`) lose enclosing
 * metadata references. When Jackson's KotlinModule invokes Kotlin reflection on these classes,
 * kotlin-reflect throws [KotlinReflectionInternalError: Unresolved class].
 *
 * This introspector traps reflection failures across all parameter, property, creator, and type
 * introspection methods, returning null or fallback values so Jackson gracefully falls back to
 * standard Java reflection and bytecode introspection. This provides a universal, first-principles
 * fix for all plugins with zero per-plugin MixIns or hardcoded names.
 */
class SafeKotlinAnnotationIntrospector(
    delegate: AnnotationIntrospector,
) : AnnotationIntrospectorPair(delegate, AnnotationIntrospector.nopInstance()) {

    override fun findDefaultCreator(
        config: MapperConfig<*>?,
        ac: AnnotatedClass?,
        creators: MutableList<PotentialCreator>?,
        implicitConstructors: MutableList<PotentialCreator>?,
    ): PotentialCreator? {
        return try {
            super.findDefaultCreator(config, ac, creators, implicitConstructors)
        } catch (_: Throwable) {
            null
        }
    }

    override fun findImplicitPropertyName(member: AnnotatedMember?): String? {
        return try {
            super.findImplicitPropertyName(member)
        } catch (_: Throwable) {
            null
        }
    }

    override fun hasRequiredMarker(m: AnnotatedMember?): Boolean? {
        return try {
            super.hasRequiredMarker(m)
        } catch (_: Throwable) {
            null
        }
    }

    override fun findCreatorAnnotation(config: MapperConfig<*>?, a: Annotated?): JsonCreator.Mode? {
        return try {
            super.findCreatorAnnotation(config, a)
        } catch (_: Throwable) {
            null
        }
    }

    override fun hasIgnoreMarker(m: AnnotatedMember?): Boolean {
        return try {
            super.hasIgnoreMarker(m)
        } catch (_: Throwable) {
            false
        }
    }

    override fun isIgnorableType(ac: AnnotatedClass?): Boolean? {
        return try {
            super.isIgnorableType(ac)
        } catch (_: Throwable) {
            null
        }
    }

    override fun findNameForSerialization(a: Annotated?): PropertyName? {
        return try {
            super.findNameForSerialization(a)
        } catch (_: Throwable) {
            null
        }
    }

    override fun findNameForDeserialization(a: Annotated?): PropertyName? {
        return try {
            super.findNameForDeserialization(a)
        } catch (_: Throwable) {
            null
        }
    }

    override fun findPropertyDescription(a: Annotated?): String? {
        return try {
            super.findPropertyDescription(a)
        } catch (_: Throwable) {
            null
        }
    }

    override fun findSerializer(a: Annotated?): Any? {
        return try {
            super.findSerializer(a)
        } catch (_: Throwable) {
            null
        }
    }

    override fun findDeserializer(a: Annotated?): Any? {
        return try {
            super.findDeserializer(a)
        } catch (_: Throwable) {
            null
        }
    }

    override fun findNullSerializer(a: Annotated?): Any? {
        return try {
            super.findNullSerializer(a)
        } catch (_: Throwable) {
            null
        }
    }

    override fun findValueInstantiator(ac: AnnotatedClass?): Any? {
        return try {
            super.findValueInstantiator(ac)
        } catch (_: Throwable) {
            null
        }
    }

    override fun refineDeserializationType(config: MapperConfig<*>?, a: Annotated?, baseType: JavaType?): JavaType? {
        return try {
            super.refineDeserializationType(config, a, baseType)
        } catch (_: Throwable) {
            baseType
        }
    }

    override fun refineSerializationType(config: MapperConfig<*>?, a: Annotated?, baseType: JavaType?): JavaType? {
        return try {
            super.refineSerializationType(config, a, baseType)
        } catch (_: Throwable) {
            baseType
        }
    }
}
