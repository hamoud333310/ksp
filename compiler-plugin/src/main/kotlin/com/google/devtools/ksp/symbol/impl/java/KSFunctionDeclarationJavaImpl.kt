/*
 * Copyright 2020 Google LLC
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.google.devtools.ksp.symbol.impl.java

import com.intellij.lang.jvm.JvmModifier
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import com.google.devtools.ksp.isOpen
import com.google.devtools.ksp.isVisibleFrom
import com.google.devtools.ksp.processing.impl.ResolverImpl
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.symbol.impl.*
import com.google.devtools.ksp.symbol.impl.kotlin.KSExpectActualNoImpl
import com.google.devtools.ksp.symbol.impl.kotlin.KSNameImpl
import org.jetbrains.kotlin.resolve.OverridingUtil

class KSFunctionDeclarationJavaImpl private constructor(val psi: PsiMethod) : KSFunctionDeclaration, KSDeclarationJavaImpl(),
    KSExpectActual by KSExpectActualNoImpl() {
    companion object : KSObjectCache<PsiMethod, KSFunctionDeclarationJavaImpl>() {
        fun getCached(psi: PsiMethod) = cache.getOrPut(psi) { KSFunctionDeclarationJavaImpl(psi) }
    }

    override val origin = Origin.JAVA

    override val location: Location by lazy {
        psi.toLocation()
    }

    override val annotations: List<KSAnnotation> by lazy {
        psi.annotations.map { KSAnnotationJavaImpl.getCached(it) }
    }

    override val containingFile: KSFile? by lazy {
        KSFileJavaImpl.getCached(psi.containingFile as PsiJavaFile)
    }

    override fun findOverridee(): KSFunctionDeclaration? {
        return ResolverImpl.instance.resolveFunctionDeclaration(this)?.original?.overriddenDescriptors?.single { it.overriddenDescriptors.isEmpty() }
            ?.toKSFunctionDeclaration()
    }

    override fun overrides(overridee: KSFunctionDeclaration): Boolean {
        if (!overridee.isOpen())
            return false
        if (!overridee.isVisibleFrom(this))
            return false
        val superDescriptor = ResolverImpl.instance.resolveFunctionDeclaration(overridee) ?: return false
        val subDescriptor = ResolverImpl.instance.resolveJavaDeclaration(psi) as? FunctionDescriptor ?: return false
        return OverridingUtil.DEFAULT.isOverridableBy(
            superDescriptor, subDescriptor, null
        ).result == OverridingUtil.OverrideCompatibilityInfo.Result.OVERRIDABLE
    }

    override val declarations: List<KSDeclaration> = emptyList()

    override val extensionReceiver: KSTypeReference? = null

    override val functionKind: FunctionKind = if (psi.hasModifier(JvmModifier.STATIC)) FunctionKind.STATIC else FunctionKind.MEMBER

    override val isAbstract: Boolean by lazy {
        this.modifiers.contains(Modifier.ABSTRACT) ||
                ((this.parentDeclaration as? KSClassDeclaration)?.classKind == ClassKind.INTERFACE &&
                        !this.modifiers.contains(Modifier.JAVA_DEFAULT))
    }

    override val modifiers: Set<Modifier> by lazy {
        psi.toKSModifiers()
    }

    override val parameters: List<KSValueParameter> by lazy {
        psi.parameterList.parameters.map { KSValueParameterJavaImpl.getCached(it) }
    }

    override val parentDeclaration: KSDeclaration? by lazy {
        psi.findParentDeclaration()
    }

    override val qualifiedName: KSName by lazy {
        KSNameImpl.getCached("${parentDeclaration?.qualifiedName?.asString()}.${this.simpleName.asString()}")
    }

    override val returnType: KSTypeReference? by lazy {
        if (psi.returnType != null) {
            KSTypeReferenceJavaImpl.getCached(psi.returnType!!)
        } else {
            null
        }
    }

    override val simpleName: KSName by lazy {
        KSNameImpl.getCached(psi.name)
    }

    override val typeParameters: List<KSTypeParameter> by lazy {
        psi.typeParameters.map { KSTypeParameterJavaImpl.getCached(it) }
    }

    override fun <D, R> accept(visitor: KSVisitor<D, R>, data: D): R {
        return visitor.visitFunctionDeclaration(this, data)
    }
}