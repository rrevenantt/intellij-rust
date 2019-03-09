package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.psi.SearchUtils
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsFieldsOwner
import org.rust.lang.core.psi.ext.ancestorOrSelf
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.descendantOfTypeStrict

class ConvertToStructIntention : RsElementBaseIntentionAction<ConvertToStructIntention.Context>() {

    override fun getText() = "Convert to struct"
    override fun getFamilyName() = text

    class Context(val element: RsFieldsOwner)

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): ConvertToStructIntention.Context? {
        val struct = element.ancestorStrict<RsFieldsOwner>() ?: return null
        if (struct.tupleFields == null || struct.descendantOfTypeStrict<PsiErrorElement>() != null) return null
        return Context(struct)
    }

    override fun invoke(project: Project, editor: Editor, ctx: ConvertToStructIntention.Context) {
        val types = ctx.element.tupleFields!!.tupleFieldDeclList
        val fields = RsPsiFactory(project).createBlockFields(types.mapIndexedNotNull { index, rsTupleFieldDecl ->
            RsPsiFactory.BlockField(rsTupleFieldDecl.vis != null, "_$index", rsTupleFieldDecl.typeReference)
        })

        val usages = SearchUtils.findAllReferences(ctx.element)
        val canJustRename = { parent: PsiElement ->
            parent is RsDotExpr || parent is RsStructLiteralField || parent is RsPatField
        }

        //convert field usages as block struct
        for ((index, tupleField) in types.withIndex()) {
            for (usage in SearchUtils.findAllReferences(tupleField)
                .filter { canJustRename(it.element.parent) }) {
                usage.element.replace(RsPsiFactory(project).createIdentifier("_$index"))
            }
        }

        //convert usages in pattern matching as tuple
        for (usage in usages.mapNotNull { it.element.ancestorOrSelf<RsPatTupleStruct>() }) {
            var dotdotPos = 0
            PsiTreeUtil.findSiblingForward(usage.lparen, RsElementTypes.DOTDOT) { if (it is RsPat) dotdotPos += 1 }

            val patternPsiElement = RsPsiFactory(project).createStatement("let ${usage.path.text}" +
                "${usage.patList.withIndex().joinToString(", ", "{")
                { "_${if (it.index < dotdotPos) it.index else (it.index + types.size - usage.patList.size)}:${it.value.text}" }} " +
                "${if (usage.dotdot != null) ", .." else ""}} = 0;").descendantOfTypeStrict<RsPatStruct>()!!
            usage.replace(patternPsiElement)
        }

        //convert usages in instance creation as tuple, like `A(0)`
        for (usage in usages.mapNotNull { it.element.ancestorOrSelf<RsCallExpr>() }) {
            val values = usage.valueArgumentList.exprList
            val structCreationElement = RsPsiFactory(project).createStatement("let a = ${usage.expr.text}" +
                values.mapIndexedNotNull { index: Int, rsExpr: RsExpr ->
                    "_$index:${rsExpr.text}"
                }.joinToString(",\n", "{", "};"))
            usage.replace(structCreationElement.descendantOfTypeStrict<RsStructLiteral>()!!)
        }

        ctx.element.tupleFields!!.replace(fields)
        (ctx.element as? RsStructItem)?.semicolon?.delete()
    }


}
