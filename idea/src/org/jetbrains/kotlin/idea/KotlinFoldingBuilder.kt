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

package org.jetbrains.kotlin.idea

import com.intellij.codeInsight.folding.JavaCodeFoldingSettings
import com.intellij.lang.ASTNode
import com.intellij.lang.folding.CustomFoldingBuilder
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.idea.util.treeNextNonWhitespace
import org.jetbrains.kotlin.kdoc.lexer.KDocTokens
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtImportList
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset

class KotlinFoldingBuilder : CustomFoldingBuilder(), DumbAware {
    override fun buildLanguageFoldRegions(descriptors: MutableList<FoldingDescriptor>,
                                          root: PsiElement, document: Document, quick: Boolean) {
        if (root !is KtFile) {
            return
        }
        val imports = root.importDirectives
        if (imports.size > 1) {
            val importKeyword = imports.get(0).firstChild
            val startOffset = importKeyword.endOffset + 1

            val importList = root.importList
            if (importList != null) {
                val endOffset = importList.endOffset

                val range = TextRange(startOffset, endOffset)
                descriptors.add(FoldingDescriptor(importList, range))
            }
        }

        appendDescriptors(root.node, document, descriptors)
    }

    private fun appendDescriptors(node: ASTNode, document: Document, descriptors: MutableList<FoldingDescriptor>) {
        if (needFolding(node)) {
            val textRange = getRangeToFold(node)
            if (!isOneLine(textRange, document)) {
                descriptors.add(if (isNewLineRequired(node, document)) KtFoldingDescriptor(node, textRange, true)
                                else FoldingDescriptor(node, textRange))
            }
        }

        var child = node.firstChildNode
        while (child != null) {
            appendDescriptors(child, document, descriptors)
            child = child.treeNext
        }
    }

    private fun needFolding(node: ASTNode): Boolean {
        val type = node.elementType
        val parentType = node.treeParent?.elementType
        return type == KtNodeTypes.FUNCTION_LITERAL ||
               (type == KtNodeTypes.BLOCK && parentType != KtNodeTypes.FUNCTION_LITERAL) ||
               type == KtNodeTypes.CLASS_BODY || type == KtTokens.BLOCK_COMMENT || type == KDocTokens.KDOC
    }

    private fun getRangeToFold(node: ASTNode): TextRange {
        if (node.elementType == KtNodeTypes.FUNCTION_LITERAL) {
            val psi = node.psi as? KtFunctionLiteral
            val lbrace = psi?.lBrace
            val rbrace = psi?.rBrace
            if (lbrace != null && rbrace != null) {
                return TextRange(lbrace.startOffset, rbrace.endOffset)
            }
        }
        return node.textRange
    }

    private fun isOneLine(textRange: TextRange, document: Document) =
        document.getLineNumber(textRange.startOffset) == document.getLineNumber(textRange.endOffset)

    override fun getLanguagePlaceholderText(node: ASTNode, range: TextRange): String = when {
        node.elementType == KtTokens.BLOCK_COMMENT -> "/.../"
        node.elementType == KDocTokens.KDOC -> "/**...*/"
        node.psi is KtImportList -> "..."
        else ->  "{...}"
    }

    private fun isNewLineRequired(node: ASTNode, document: Document): Boolean {
        if (node.elementType != KtNodeTypes.BLOCK ) return false
        val parent = node.treeParent
        val pet = parent.elementType
        val next: ASTNode?
        if (pet == KtNodeTypes.THEN || pet == KtNodeTypes.CATCH) {
            next = parent.treeNextNonWhitespace()
        }
        else if (pet == KtNodeTypes.TRY) {
            next = node.treeNextNonWhitespace()
        }
        else if (pet == KtNodeTypes.BODY && parent.treeParent.elementType == KtNodeTypes.DO_WHILE) {
            next = parent.treeNextNonWhitespace()
        }
        else {
            return false
        }
        return next != null && document.getLineNumber(next.startOffset) == document.getLineNumber(node.textRange.endOffset)
    }

    override fun isRegionCollapsedByDefault(node: ASTNode): Boolean {
        val settings = JavaCodeFoldingSettings.getInstance()

        if (node.psi is KtImportList) {
            return settings.isCollapseImports
        }

        val type = node.elementType
        if (type == KtTokens.BLOCK_COMMENT || type == KDocTokens.KDOC) {
            if (isFirstElementInFile(node.psi)) {
                return settings.isCollapseFileHeader
            }
        }

        return false
    }

    override fun isCustomFoldingRoot(node: ASTNode)
        = node.elementType == KtNodeTypes.BLOCK || node.elementType == KtNodeTypes.CLASS_BODY

    private fun isFirstElementInFile(element: PsiElement): Boolean {
        val parent = element.parent
        if (parent is KtFile) {
            val firstNonWhiteSpace = parent.allChildren.firstOrNull {
                it.textLength != 0 && it !is PsiWhiteSpace
            }

            return element == firstNonWhiteSpace
        }

        return false
    }
}
