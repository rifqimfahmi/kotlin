/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.lower.serialization.ir

import org.jetbrains.kotlin.backend.common.overrides.FakeOverrideBuilder
import org.jetbrains.kotlin.backend.common.serialization.*
import org.jetbrains.kotlin.backend.common.serialization.encodings.BinarySymbolData
import org.jetbrains.kotlin.backend.common.serialization.unlinked.UnlinkedDeclarationsSupport
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.ir.IrBuiltIns
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.builders.TranslationPluginContext
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.impl.IrModuleFragmentImpl
import org.jetbrains.kotlin.ir.symbols.IrFieldSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.types.IrTypeSystemContextImpl
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.library.IrLibrary
import org.jetbrains.kotlin.library.KotlinAbiVersion
import org.jetbrains.kotlin.library.KotlinLibrary
import org.jetbrains.kotlin.library.containsErrorCode

class JsIrLinker(
    private val currentModule: ModuleDescriptor?, messageLogger: IrMessageLogger, builtIns: IrBuiltIns, symbolTable: SymbolTable,
    override val translationPluginContext: TranslationPluginContext?,
    private val icData: ICData? = null,
    friendModules: Map<String, Collection<String>> = emptyMap(),
    override val unlinkedDeclarationsSupport: UnlinkedDeclarationsSupport = JsUnlinkedDeclarationsSupport(allowUnboundSymbols = false),
    private val stubGenerator: DeclarationStubGenerator? = null
) : KotlinIrLinker(
    currentModule = currentModule,
    messageLogger = messageLogger,
    builtIns = builtIns,
    symbolTable = symbolTable,
    exportedDependencies = emptyList(),
    symbolProcessor = { symbol, idSig ->
        if (idSig.isLocal) {
            symbol.privateSignature = IdSignature.CompositeSignature(IdSignature.FileSignature(fileSymbol), idSig)
        }
        symbol
    }) {

    override val fakeOverrideBuilder = FakeOverrideBuilder(this, symbolTable, JsManglerIr, IrTypeSystemContextImpl(builtIns), friendModules)

    override fun isBuiltInModule(moduleDescriptor: ModuleDescriptor): Boolean =
        moduleDescriptor === moduleDescriptor.builtIns.builtInsModule

    private val IrLibrary.libContainsErrorCode: Boolean
        get() = this is KotlinLibrary && this.containsErrorCode

    override fun createModuleDeserializer(
        moduleDescriptor: ModuleDescriptor,
        klib: KotlinLibrary?,
        strategyResolver: (String) -> DeserializationStrategy
    ): IrModuleDeserializer {
        require(klib != null) { "Expecting kotlin library" }
        val libraryAbiVersion = klib.versions.abiVersion ?: KotlinAbiVersion.CURRENT
        return when (val lazyIrGenerator = stubGenerator) {
            null -> JsModuleDeserializer(moduleDescriptor, klib, strategyResolver, libraryAbiVersion, klib.libContainsErrorCode)
            else -> JsLazyIrModuleDeserializer(moduleDescriptor, libraryAbiVersion, lazyIrGenerator)
        }
    }

    private inner class JsModuleDeserializer(moduleDescriptor: ModuleDescriptor, klib: IrLibrary, strategyResolver: (String) -> DeserializationStrategy, libraryAbiVersion: KotlinAbiVersion, allowErrorCode: Boolean) :
        BasicIrModuleDeserializer(this, moduleDescriptor, klib, strategyResolver, libraryAbiVersion, allowErrorCode)

    private inner class JsLazyIrModuleDeserializer(
        moduleDescriptor: ModuleDescriptor,
        libraryAbiVersion: KotlinAbiVersion,
        private val stubGenerator: DeclarationStubGenerator
    ) : IrModuleDeserializer(moduleDescriptor, libraryAbiVersion) {
        private val dependencies = emptyList<IrModuleDeserializer>()

        // TODO: implement proper check whether `idSig` belongs to this module
        override fun contains(idSig: IdSignature): Boolean = true

        private val descriptorFinder = DescriptorByIdSignatureFinderImpl(moduleDescriptor, JsManglerDesc)

        private fun resolveDescriptor(idSig: IdSignature): DeclarationDescriptor {
            return descriptorFinder.findDescriptorBySignature(idSig) ?: error("No descriptor found for $idSig")
        }

        override fun deserializeIrSymbol(idSig: IdSignature, symbolKind: BinarySymbolData.SymbolKind): IrSymbol {
            val descriptor = resolveDescriptor(idSig)

            val declaration = stubGenerator.run {
                when (symbolKind) {
                    BinarySymbolData.SymbolKind.CLASS_SYMBOL -> generateClassStub(descriptor as ClassDescriptor)
                    BinarySymbolData.SymbolKind.PROPERTY_SYMBOL -> generatePropertyStub(descriptor as PropertyDescriptor)
                    BinarySymbolData.SymbolKind.FUNCTION_SYMBOL -> generateFunctionStub(descriptor as FunctionDescriptor)
                    BinarySymbolData.SymbolKind.CONSTRUCTOR_SYMBOL -> generateConstructorStub(descriptor as ClassConstructorDescriptor)
                    BinarySymbolData.SymbolKind.ENUM_ENTRY_SYMBOL -> generateEnumEntryStub(descriptor as ClassDescriptor)
                    BinarySymbolData.SymbolKind.TYPEALIAS_SYMBOL -> generateTypeAliasStub(descriptor as TypeAliasDescriptor)
                    else -> error("Unexpected type $symbolKind for sig $idSig")
                }
            }

            return declaration.symbol
        }

        @OptIn(ObsoleteDescriptorBasedAPI::class)
        override fun declareIrSymbol(symbol: IrSymbol) {
            if (symbol is IrFieldSymbol) {
                declareFieldStub(symbol)
            } else {
                stubGenerator.generateMemberStub(symbol.descriptor)
            }
        }

        @OptIn(ObsoleteDescriptorBasedAPI::class)
        private fun declareFieldStub(symbol: IrFieldSymbol): IrField {
            return with(stubGenerator) {
                val old = stubGenerator.unboundSymbolGeneration
                try {
                    stubGenerator.unboundSymbolGeneration = true
                    generateFieldStub(symbol.descriptor)
                } finally {
                    stubGenerator.unboundSymbolGeneration = old
                }
            }
        }


        override val moduleFragment: IrModuleFragment = IrModuleFragmentImpl(moduleDescriptor, builtIns, emptyList())
        override val moduleDependencies: Collection<IrModuleDeserializer> = dependencies

        override val kind get() = IrModuleDeserializerKind.SYNTHETIC
    }

    override fun maybeWrapWithBuiltInAndInit(
        moduleDescriptor: ModuleDescriptor,
        moduleDeserializer: IrModuleDeserializer
    ): IrModuleDeserializer {
        return if (isBuiltInModule(moduleDescriptor)) {
            IrModuleDeserializerWithBuiltIns(builtIns, moduleDeserializer)
        } else moduleDeserializer
    }

    override fun createCurrentModuleDeserializer(moduleFragment: IrModuleFragment, dependencies: Collection<IrModuleDeserializer>): IrModuleDeserializer {
        val currentModuleDeserializer = super.createCurrentModuleDeserializer(moduleFragment, dependencies)
        icData?.let {
            return CurrentModuleWithICDeserializer(currentModuleDeserializer, symbolTable, builtIns, it.icData) { lib ->
                JsModuleDeserializer(currentModuleDeserializer.moduleDescriptor, lib, currentModuleDeserializer.strategyResolver, KotlinAbiVersion.CURRENT, it.containsErrorCode)
            }
        }
        return currentModuleDeserializer
    }

    val modules
        get() = deserializersForModules.values
            .map { it.moduleFragment }
            .filter { it.descriptor !== currentModule }


    fun moduleDeserializer(moduleDescriptor: ModuleDescriptor): IrModuleDeserializer {
        return deserializersForModules[moduleDescriptor.name.asString()] ?: error("Deserializer for $moduleDescriptor not found")
    }

    class JsFePluginContext(
        override val moduleDescriptor: ModuleDescriptor,
        override val symbolTable: ReferenceSymbolTable,
        override val typeTranslator: TypeTranslator,
        override val irBuiltIns: IrBuiltIns,
    ) : TranslationPluginContext
}
