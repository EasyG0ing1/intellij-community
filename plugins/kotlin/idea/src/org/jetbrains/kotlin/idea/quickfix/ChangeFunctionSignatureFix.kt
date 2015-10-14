/*
 * Copyright 2010-2015 JetBrains s.r.o.
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

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor.Kind.SYNTHESIZED
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.diagnostics.Errors.EXPECTED_PARAMETERS_NUMBER_MISMATCH
import org.jetbrains.kotlin.diagnostics.Errors.UNUSED_PARAMETER
import org.jetbrains.kotlin.idea.JetBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptor
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.core.KotlinNameSuggester
import org.jetbrains.kotlin.idea.core.quickfix.QuickFixUtil
import org.jetbrains.kotlin.idea.refactoring.changeSignature.JetParameterInfo
import org.jetbrains.kotlin.idea.refactoring.changeSignature.JetValVar
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.psi.JetCallElement
import org.jetbrains.kotlin.psi.JetFunctionLiteral
import org.jetbrains.kotlin.psi.ValueArgument
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.descriptorUtil.builtIns
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.JetType
import org.jetbrains.kotlin.types.checker.JetTypeChecker

abstract class ChangeFunctionSignatureFix(
        protected val context: PsiElement,
        protected val functionDescriptor: FunctionDescriptor
) : KotlinQuickFixAction<PsiElement>(context) {

    override fun getFamilyName() = JetBundle.message("change.signature.family")

    override fun startInWriteAction() = false

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile): Boolean {
        if (!super.isAvailable(project, editor, file)) {
            return false
        }

        val declarations = DescriptorToSourceUtilsIde.getAllDeclarations(project, functionDescriptor)
        if (declarations.isEmpty()) {
            return false
        }

        for (declaration in declarations) {
            if (!declaration.isValid || !QuickFixUtil.canModifyElement(declaration)) {
                return false
            }
        }

        return true
    }

    protected fun getNewArgumentName(argument: ValueArgument, validator: Function1<String, Boolean>): String {
        val argumentName = argument.getArgumentName()
        val expression = argument.getArgumentExpression()

        if (argumentName != null) {
            return KotlinNameSuggester.suggestNameByName(argumentName.asName.asString(), validator)
        } else if (expression != null) {
            val bindingContext = expression.analyze(BodyResolveMode.PARTIAL)
            return KotlinNameSuggester.suggestNamesByExpressionAndType(expression, bindingContext, validator, "param").iterator().next()
        } else {
            return KotlinNameSuggester.suggestNameByName("param", validator)
        }
    }

    protected fun getNewParameterInfo(
            functionDescriptor: FunctionDescriptor,
            bindingContext: BindingContext,
            argument: ValueArgument,
            validator: Function1<String, Boolean>): JetParameterInfo {
        val name = getNewArgumentName(argument, validator)
        val expression = argument.getArgumentExpression()
        var type: JetType? = if (expression != null) bindingContext.getType(expression) else null
        type = if (type != null) type else functionDescriptor.builtIns.nullableAnyType
        val parameterInfo = JetParameterInfo(functionDescriptor, -1, name, type, null, null, JetValVar.None, null)
        parameterInfo.currentTypeText = IdeDescriptorRenderers.SOURCE_CODE.renderType(type)

        return parameterInfo
    }

    companion object {

        fun createFactory(): JetSingleIntentionActionFactory {
            return object : JetSingleIntentionActionFactory() {
                public override fun createAction(diagnostic: Diagnostic): ChangeFunctionSignatureFix? {
                    val callElement = PsiTreeUtil.getParentOfType(diagnostic.psiElement, JetCallElement::class.java)
                    //noinspection unchecked
                    val descriptor = DiagnosticFactory.cast(diagnostic, Errors.TOO_MANY_ARGUMENTS, Errors.NO_VALUE_FOR_PARAMETER).a

                    if (callElement != null) {
                        return createFix(callElement, callElement, descriptor)
                    }

                    return null
                }
            }
        }

        fun createFactoryForParametersNumberMismatch(): JetSingleIntentionActionFactory {
            return object : JetSingleIntentionActionFactory() {
                public override fun createAction(diagnostic: Diagnostic): ChangeFunctionSignatureFix? {
                    val diagnosticWithParameters = EXPECTED_PARAMETERS_NUMBER_MISMATCH.cast(diagnostic)
                    val functionLiteral = diagnosticWithParameters.psiElement
                    val descriptor = functionLiteral.resolveToDescriptor()

                    if (descriptor is FunctionDescriptor && functionLiteral is JetFunctionLiteral) {
                        return ChangeFunctionLiteralSignatureFix(functionLiteral, descriptor,
                                diagnosticWithParameters.b)
                    } else {
                        return null
                    }
                }
            }
        }

        fun createFactoryForUnusedParameter(): JetSingleIntentionActionFactory {
            return object : JetSingleIntentionActionFactory() {
                public override fun createAction(diagnostic: Diagnostic): ChangeFunctionSignatureFix? {
                    @SuppressWarnings("unchecked")
                    val descriptor = UNUSED_PARAMETER.cast(diagnostic).a

                    if (descriptor is ValueParameterDescriptor) {
                        return createFix(null, diagnostic.psiElement, descriptor as CallableDescriptor)
                    } else {
                        return null
                    }
                }
            }
        }

        private fun createFix(callElement: JetCallElement?, context: PsiElement, descriptor: CallableDescriptor): ChangeFunctionSignatureFix? {
            var functionDescriptor: FunctionDescriptor? = null

            if (descriptor is FunctionDescriptor) {
                functionDescriptor = descriptor
            } else if (descriptor is ValueParameterDescriptor) {
                val containingDescriptor = descriptor.containingDeclaration

                if (containingDescriptor is FunctionDescriptor) {
                    functionDescriptor = containingDescriptor
                }
            }

            if (functionDescriptor == null) {
                return null
            }

            if (functionDescriptor.kind == SYNTHESIZED) {
                return null
            }

            if (descriptor is ValueParameterDescriptor) {
                return RemoveFunctionParametersFix(context, functionDescriptor, descriptor)
            } else {
                val parameters = functionDescriptor.valueParameters
                val arguments = callElement!!.valueArguments

                if (arguments.size() > parameters.size()) {
                    val bindingContext = callElement.analyze()
                    val hasTypeMismatches = hasTypeMismatches(parameters, arguments, bindingContext)
                    return AddFunctionParametersFix(callElement, functionDescriptor, hasTypeMismatches)
                }
            }

            return null
        }

        private fun hasTypeMismatches(
                parameters: List<ValueParameterDescriptor>,
                arguments: List<ValueArgument>,
                bindingContext: BindingContext): Boolean {
            for (i in parameters.indices) {
                assert(i < arguments.size()) // number of parameters must not be greater than the number of arguments (it's called only for TOO_MANY_ARGUMENTS error)
                val argumentExpression = arguments.get(i).getArgumentExpression()
                val argumentType = if (argumentExpression != null) bindingContext.getType(argumentExpression) else null
                val parameterType = parameters.get(i).type

                if (argumentType == null || !JetTypeChecker.DEFAULT.isSubtypeOf(argumentType, parameterType)) {
                    return true
                }
            }

            return false
        }
    }
}
