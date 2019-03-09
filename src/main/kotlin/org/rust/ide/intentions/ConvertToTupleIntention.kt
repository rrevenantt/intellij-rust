package org.rust.ide.intentions

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.impl.ProgressManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.ReadonlyStatusHandler
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.refactoring.psi.SearchUtils
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*


class ConvertToTupleIntention : RsElementBaseIntentionAction<ConvertToTupleIntention.Context>() {
    override fun getText() = "Convert to tuple"
    override fun getFamilyName() = text

    class Context(val element: RsFieldsOwner)

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): ConvertToTupleIntention.Context? {
        val struct = element.ancestorStrict<RsFieldsOwner>() ?: return null
        if (struct.blockFields == null
            || struct.descendantOfTypeStrict<PsiErrorElement>() != null) return null
        return Context(struct)
    }

    override fun startInWriteAction(): Boolean = false

    override fun invoke(project: Project, editor: Editor, ctx: ConvertToTupleIntention.Context) {
        ReadonlyStatusHandler.ensureFilesWritable(project, ctx.element.containingFile.virtualFile)
        ProgressManagerImpl.getInstance().run(object : Task.Modal(project, "Converting", false) {
            override fun run(indicator: ProgressIndicator) {
                indicator.fraction = 0.1
                indicator.text = "Searching for struct usages"
//                Thread.sleep(1000)
                val fieldDeclList = runReadAction { ctx.element.blockFields!!.namedFieldDeclList }

                val usages = runReadAction { SearchUtils.findAllReferences(ctx.element) }

                indicator.fraction = 0.3
                indicator.text = "Converting field access usages"
                WriteCommandAction.runWriteCommandAction(project, "test", "test", Runnable {
                    for (structField in fieldDeclList) {
                        val usagesInDotExpr = SearchUtils.findAllReferences(structField).mapNotNull { it.element }.filter { it.parent.elementType == RsElementTypes.DOT_EXPR }
                        for (usage in usagesInDotExpr) {
                            usage.replace(RsPsiFactory(project).createExpression("a." + fieldDeclList.map { it.identifier.text }.indexOf(usage.text).toString())
                                .descendantOfTypeStrict<RsFieldLookup>()!!)
                        }
//                            Thread.sleep(1000)
                    }
                })

                indicator.fraction = 0.5
                indicator.text = "Converting pattern matching usages"

                //convert usages in pattern matching
                WriteCommandAction.runWriteCommandAction(project, "test", "test", Runnable {
                    for (usage in usages.mapNotNull { it.element.ancestorOrSelf<RsPatStruct>() }) {
//                            Thread.sleep(3000)
                        val patternFieldMap = usage.patFieldList.associate {
                            val kind = it.kind
                            when (kind) {
                                is RsPatFieldKind.Full -> kind.fieldName to kind.pat.text
                                is RsPatFieldKind.Shorthand -> kind.fieldName to kind.binding.text

                            }
                        }
                        val patternPsiElement = RsPsiFactory(project).createStatement("let ${usage.path.text}" +
                            fieldDeclList
                                .joinToString(", ", "(", ")") {
                                    patternFieldMap[it.identifier.text] ?: "_ "
                                } + " = 0;")
                            .descendantOfTypeStrict<RsPatTupleStruct>()!!

                        CodeStyleManager.getInstance(project).reformat(patternPsiElement)
                        usage.replace(patternPsiElement)

                    }
                })

                indicator.fraction = 0.7
                indicator.text = "Converting instance creation"
                //convert usages in instance creation
                WriteCommandAction.runWriteCommandAction(project, "test", "test", Runnable {
                    //                    Thread.sleep(1000)
                    for (usage in usages.mapNotNull { it.element.ancestorOrSelf<RsStructLiteral>() }) {
                        if (usage.structLiteralBody.dotdot != null) {

                            val expr = usage.structLiteralBody.structLiteralFieldList.joinToString(",", "let a = X{") {
                                "${fieldDeclList.indexOfFirst { inner -> inner.identifier.textMatches(it.identifier!!) }}:${it.expr?.text
                                    ?: it.identifier!!.text}"
                            } + ", ..${usage.structLiteralBody.expr!!.text}}"
                            usage.structLiteralBody.replace(RsPsiFactory(project).createExpression(expr).descendantOfTypeStrict<RsStructLiteralBody>()!!)
                            CodeStyleManager.getInstance(project).reformat(usage)
                        } else {
                            val valuesMap = usage.structLiteralBody.structLiteralFieldList.associate {
                                it.identifier!!.text to (it.expr?.text ?: it.identifier!!.text)
                            }
                            val structCreationElement = RsPsiFactory(project).createStatement("let a = ${usage.path.text}(" +
                                //need to restore order of fields
                                fieldDeclList.joinToString(", ") {
                                    if (valuesMap.containsKey(it.identifier.text)) valuesMap[it.identifier.text]
                                        ?: "" else "_ "
                                } + ");").descendantOfTypeStrict<RsCallExpr>()!!

                            CodeStyleManager.getInstance(project).reformat(structCreationElement)
                            usage.replace(structCreationElement)
                        }

                    }
                    val types = fieldDeclList.mapNotNull {
                        "${it.vis?.text ?: ""} ${it.typeReference?.text}"
                    }.joinToString(",", "(", ")")

                    val newTuplePsiElement =
                        if (ctx.element is RsStructItem)
                            RsPsiFactory(project).createStruct("${ctx.element.vis?.text
                                ?: ""} struct ${ctx.element.nameIdentifier?.text}$types;")
                        else
                            RsPsiFactory(project).createEnum("enum A{ ${ctx.element.nameIdentifier?.text}$types}").descendantOfTypeStrict<RsEnumVariant>()!!

                    CodeStyleManager.getInstance(project).reformat(newTuplePsiElement)
                    ctx.element.replace(newTuplePsiElement)
                })
            }
        })
    }

}
