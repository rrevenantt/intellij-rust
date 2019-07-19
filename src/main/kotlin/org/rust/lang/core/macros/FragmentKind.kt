/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.lang.PsiBuilder
import com.intellij.lang.parser.GeneratedParserUtilBase
import com.intellij.psi.tree.TokenSet
import org.rust.lang.core.parser.RustParser
import org.rust.lang.core.psi.RS_KEYWORDS
import org.rust.lang.core.psi.RsElementTypes
import org.rust.lang.core.psi.tokenSetOf

enum class FragmentKind {
    Ident,
    Path,
    Expr,
    Ty,
    Pat,
    Stmt,
    Block,
    Item,
    Meta,
    Tt,
    Vis,
    Literal,
    Lifetime;

    fun parse(builder: PsiBuilder): Boolean {
        return if (this == Ident) {
            parseIdentifier(builder)
        } else {
            // we use similar logic as in org.rust.lang.core.parser.RustParser#parseLight
            val root = RsElementTypes.FUNCTION
            val adaptBuilder = GeneratedParserUtilBase.adapt_builder_(
                root,
                builder,
                RustParser(),
                RustParser.EXTENDS_SETS_
            )
            val marker = GeneratedParserUtilBase
                .enter_section_(adaptBuilder, 0, GeneratedParserUtilBase._COLLAPSE_, null)

            val parsed = when (this) {
                Path -> RustParser.PathGenericArgsWithColons(adaptBuilder, 0)
                Expr -> RustParser.Expr(adaptBuilder, 0, -1)
                Ty -> RustParser.TypeReference(adaptBuilder, 0)
                Pat -> RustParser.Pat(adaptBuilder, 0)
                Stmt -> parseStatement(adaptBuilder)
                Block -> RustParser.Block(adaptBuilder, 0)
                Item -> parseItem(adaptBuilder)
                Meta -> RustParser.MetaItemWithoutTT(adaptBuilder, 0)
                Vis -> parseVis(adaptBuilder)
                Tt -> RustParser.TT(adaptBuilder, 0)
                Lifetime -> RustParser.Lifetime(adaptBuilder, 0)
                Literal -> RustParser.LitExpr(adaptBuilder, 0)
                Ident -> false // impossible
            }
            GeneratedParserUtilBase.exit_section_(adaptBuilder, 0, marker, root, parsed, true) { _, _ -> false }
            parsed
        }
    }

    private fun parseIdentifier(b: PsiBuilder): Boolean {
        return if (b.tokenType in IDENTIFIER_TOKENS) {
            b.advanceLexer()
            true
        } else {
            false
        }
    }

    private fun parseStatement(b: PsiBuilder): Boolean =
        RustParser.LetDecl(b, 0) || RustParser.Expr(b, 0, -1)

    private fun parseItem(b: PsiBuilder): Boolean =
        parseItemFns.any { it(b, 0) }

    private fun parseVis(b: PsiBuilder): Boolean {
        RustParser.Vis(b, 0)
        return true // Vis can be empty
    }

    companion object {
        fun fromString(s: String): FragmentKind? = when (s) {
            "ident" -> Ident
            "path" -> Path
            "expr" -> Expr
            "ty" -> Ty
            "pat" -> Pat
            "stmt" -> Stmt
            "block" -> Block
            "item" -> Item
            "meta" -> Meta
            "vis" -> Vis
            "tt" -> Tt
            "lifetime" -> Lifetime
            "literal" -> Literal
            else -> null
        }

        /**
         * Some tokens that treated as keywords by our lexer,
         * but rustc's macro parser treats them as identifiers
         */
        private val IDENTIFIER_TOKENS = TokenSet.orSet(tokenSetOf(RsElementTypes.IDENTIFIER), RS_KEYWORDS)

        private val parseItemFns = listOf(
            RustParser::Constant,
            RustParser::TypeAlias,
            RustParser::Function,
            RustParser::TraitItem,
            RustParser::ImplItem,
            RustParser::ModItem,
            RustParser::ModDeclItem,
            RustParser::ForeignModItem,
            RustParser::StructItem,
            RustParser::EnumItem,
            RustParser::UseItem,
            RustParser::ExternCrateItem,
            RustParser::MacroCall
        )
    }
}
