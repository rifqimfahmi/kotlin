/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.serialization.unlinked

import org.jetbrains.kotlin.backend.common.serialization.IrModuleDeserializer
import org.jetbrains.kotlin.backend.common.serialization.IrModuleDeserializerKind
import org.jetbrains.kotlin.backend.common.serialization.encodings.BinarySymbolData
import org.jetbrains.kotlin.backend.common.serialization.referenceDeserializedSymbol
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.util.IdSignature
import org.jetbrains.kotlin.ir.util.ReferenceSymbolTable
import org.jetbrains.kotlin.library.KotlinAbiVersion

internal class IrModuleWithUnboundSymbolsDeserializer(
    private val symbolTable: ReferenceSymbolTable
) : IrModuleDeserializer(null, KotlinAbiVersion.CURRENT) {

    override fun contains(idSig: IdSignature): Boolean = false

    override fun tryDeserializeIrSymbol(idSig: IdSignature, symbolKind: BinarySymbolData.SymbolKind): IrSymbol =
        referenceDeserializedSymbol(symbolTable, null, symbolKind, idSig)

    override val moduleFragment: IrModuleFragment get() = error("Unsupported operation")
    override val moduleDependencies: Collection<IrModuleDeserializer> get() = emptyList()
    override val kind: IrModuleDeserializerKind get() = error("Unsupported operation")
}