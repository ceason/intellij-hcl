/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.intellij.plugins.hcl.codeinsight

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiElement
import org.intellij.plugins.hcl.HCLSyntaxHighlighterFactory
import org.intellij.plugins.hcl.psi.HCLIdentifier
import org.intellij.plugins.hcl.psi.HCLNumberLiteral
import org.intellij.plugins.hcl.psi.HCLPsiUtil
import org.intellij.plugins.hcl.psi.HCLStringLiteral
import java.util.regex.Pattern

/**
 * Inspired by com.intellij.json.codeinsight.JsonLiteralAnnotator
 */
class HCLLiteralAnnotator : Annotator {
  companion object {
    // TODO: Check HCL supported escapes
    private val DQS_VALID_ESCAPE = Pattern.compile("\\\\([\"\\\\/bfnrt]|u[0-9a-fA-F]{4})")
    private val SQS_VALID_ESCAPE = Pattern.compile("\\\\([\'\\\\/bfnrt]|u[0-9a-fA-F]{4})")
    // TODO: AFAIK that should be handled by lexer/parser
    private val VALID_NUMBER_LITERAL = Pattern.compile("-?(0x)?(0|[1-9])\\d*(\\.\\d+)?([eE][+-]?\\d+)?")

    private val DEBUG = ApplicationManager.getApplication().isUnitTestMode
  }

  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    val text = HCLPsiUtil.getElementTextWithoutHostEscaping(element)
    if (element is HCLStringLiteral || element is HCLIdentifier) {
      if (HCLPsiUtil.isPropertyKey(element)) {
        holder.createInfoAnnotation(element, if (DEBUG) "property key" else null).textAttributes = HCLSyntaxHighlighterFactory.HCL_PROPERTY_KEY
      }
    }
    if (element is HCLStringLiteral) {
      val length = text.length

      // Check that string literal is closed properly
      if (length <= 1 || text[0] != text[length - 1] || HCLPsiUtil.isEscapedChar(text, length - 1)) {
        holder.createErrorAnnotation(element, "Missing closing quote").registerUniversalFix(AddClosingQuoteQuickFix(element), null, null)
      }

      val pattern = when (element.quoteSymbol) {
        '\'' -> SQS_VALID_ESCAPE
        '\"' -> DQS_VALID_ESCAPE
        else -> throw IllegalStateException("Unexpected string quote symbol '${element.quoteSymbol}'")
      }

      val elementOffset = element.getTextOffset()
      for (fragment in element.textFragments) {
        val fragmentText = fragment.getSecond()
        if (fragmentText.startsWith("\\") && fragmentText.length > 1 && !pattern.matcher(fragmentText).matches()) {
          val fragmentRange = fragment.getFirst()
          if (fragmentText.startsWith("\\u")) {
            holder.createErrorAnnotation(fragmentRange.shiftRight(elementOffset), "Illegal unicode escape sequence")
          } else {
            holder.createErrorAnnotation(fragmentRange.shiftRight(elementOffset), "Illegal escape sequence")
          }
        }
      }
    } else if (element is HCLNumberLiteral) {
      if (!VALID_NUMBER_LITERAL.matcher(text).matches()) {
        holder.createErrorAnnotation(element, "Illegal number literal")
      }
    }
  }
}

