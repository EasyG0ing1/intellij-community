/*
 * Copyright 2010-2016 JetBrains s.r.o.
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

package org.jetbrains.kotlin.uast

import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UImportStatement
import org.jetbrains.uast.kinds.UastImportKind
import org.jetbrains.uast.psi.PsiElementBacked

class KotlinUImportStatement(
        override val psi: KtImportDirective,
        override val parent: UElement
) : KotlinAbstractUElement(), UImportStatement, PsiElementBacked {
    override val fqNameToImport = psi.importedFqName?.asString()

    //TODO support member imports
    override val kind: UastImportKind
        get() = UastImportKind.CLASS

    override val isStarImport: Boolean
        get() = psi.isAllUnder
}