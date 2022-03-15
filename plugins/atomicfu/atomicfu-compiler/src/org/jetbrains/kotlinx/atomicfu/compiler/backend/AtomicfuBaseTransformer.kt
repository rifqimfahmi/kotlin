/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlinx.atomicfu.compiler.backend

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.symbols.isPublicApi
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.IdSignature
import org.jetbrains.kotlin.ir.util.classId
import org.jetbrains.kotlin.ir.util.parentClassOrNull

private const val AFU_PKG = "kotlinx.atomicfu"
private const val LOCKS = "locks"
private const val AFU_LOCKS_PKG = "$AFU_PKG.$LOCKS"
private const val REENTRANT_LOCK_TYPE = "ReentrantLock"
private const val TRACE_BASE_TYPE = "TraceBase"
private const val GET = "get"
private const val GET_VALUE = "getValue"
private const val SET_VALUE = "setValue"
private const val ATOMIC_VALUE_FACTORY = "atomic"
private const val TRACE = "Trace"
private const val INVOKE = "invoke"
private const val APPEND = "append"
private const val ATOMIC_ARRAY_OF_NULLS_FACTORY = "atomicArrayOfNulls"
private const val REENTRANT_LOCK_FACTORY = "reentrantLock"

abstract class AtomicfuBaseTransformer(val context: IrPluginContext) {

    protected val irBuiltIns = context.irBuiltIns

    protected val AFU_CLASSES: Map<String, IrType> = mapOf(
        "AtomicInt" to irBuiltIns.intType,
        "AtomicLong" to irBuiltIns.longType,
        "AtomicBoolean" to irBuiltIns.booleanType,
        "AtomicRef" to irBuiltIns.anyNType
    )

    protected val ATOMIC_VALUE_TYPES = setOf("AtomicInt", "AtomicLong", "AtomicBoolean", "AtomicRef")
    protected val ATOMIC_ARRAY_TYPES = setOf("AtomicIntArray", "AtomicLongArray", "AtomicBooleanArray", "AtomicArray")

    protected fun IrFunction.isAtomicExtension(): Boolean =
        extensionReceiverParameter?.let { it.type.isAtomicValueType() && this.isInline } ?: false

    protected fun IrSimpleFunctionSymbol.isKotlinxAtomicfuPackage(): Boolean =
        owner.parentClassOrNull?.classId?.let {
            it.packageFqName.asString() == AFU_PKG
        } ?: false

    abstract fun IrType.isAtomicValueType(): Boolean
    abstract fun IrType.atomicToValueType(): IrType

    private fun IrType.isReentrantLockType() = belongsTo(AFU_LOCKS_PKG, REENTRANT_LOCK_TYPE)
    private fun IrType.isTraceBaseType() = belongsTo(AFU_PKG, TRACE_BASE_TYPE)

    protected fun IrType.belongsTo(packageName: String, typeNames: Set<String>) =
        getSignature()?.let { sig ->
            sig.packageFqName == packageName && sig.declarationFqName in typeNames
        } ?: false

    private fun IrType.belongsTo(packageName: String, typeName: String) =
        getSignature()?.let { sig ->
            sig.packageFqName == packageName && sig.declarationFqName == typeName
        } ?: false

    private fun IrType.getSignature(): IdSignature.CommonSignature? = classOrNull?.let { it.signature?.asPublic() }

    protected fun IrCall.isAtomicFactory(): Boolean =
        symbol.isKotlinxAtomicfuPackage() && symbol.owner.name.asString() == ATOMIC_VALUE_FACTORY &&
                type.isAtomicValueType()

    protected fun IrCall.isTraceFactory(): Boolean =
        symbol.isKotlinxAtomicfuPackage() && symbol.owner.name.asString() == TRACE &&
                type.isTraceBaseType()



    protected fun IrCall.isAtomicFieldGetter(): Boolean =
        type.isAtomicValueType() && symbol.owner.name.asString().startsWith("<get-")

    protected fun IrCall.isReentrantLockFactory(): Boolean =
        symbol.owner.name.asString() == REENTRANT_LOCK_FACTORY && type.isReentrantLockType()

    protected fun IrCall.isGetValue() = symbol.isKotlinxAtomicfuPackage() && symbol.owner.name.asString() == GET_VALUE
    protected fun IrCall.isSetValue() = symbol.isKotlinxAtomicfuPackage() && symbol.owner.name.asString() == SET_VALUE

    protected fun IrCall.isTraceInvoke(): Boolean =
        symbol.isKotlinxAtomicfuPackage() &&
                symbol.owner.name.asString() == INVOKE &&
                symbol.owner.dispatchReceiverParameter?.type?.isTraceBaseType() == true

    protected fun IrCall.isTraceAppend(): Boolean =
        symbol.isKotlinxAtomicfuPackage() &&
                symbol.owner.name.asString() == APPEND &&
                symbol.owner.dispatchReceiverParameter?.type?.isTraceBaseType() == true

}