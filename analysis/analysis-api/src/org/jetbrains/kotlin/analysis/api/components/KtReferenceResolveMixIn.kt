/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.components

import org.jetbrains.kotlin.analysis.api.symbols.KtSymbol
import org.jetbrains.kotlin.idea.references.KtReference

public interface KtReferenceResolveMixIn : KtAnalysisSessionMixIn {
    public fun KtReference.resolveToSymbols(): Collection<KtSymbol> {
        return analysisSession.referenceResolveProvider.resolveToSymbols(this)
    }

    public fun KtReference.resolveToSymbol(): KtSymbol? {
        return resolveToSymbols().singleOrNull()
    }
}