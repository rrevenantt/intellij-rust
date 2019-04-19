/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.test

import com.intellij.execution.Location
import com.intellij.execution.PsiLocation
import com.intellij.execution.testframework.sm.runner.SMTestLocator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.psi.ext.RsQualifiedNamedElement
import org.rust.lang.core.psi.ext.qualifiedName
import org.rust.lang.core.stubs.index.RsNamedElementIndex

object CargoTestLocator : SMTestLocator {
    private const val NAME_SEPARATOR: String = "::"
    private const val TEST_PROTOCOL: String = "cargo:test"
    private const val SUITE_PROTOCOL: String = "cargo:suite"

    override fun getLocation(
        protocol: String,
        path: String,
        project: Project,
        scope: GlobalSearchScope
    ): List<Location<PsiElement>> {
        if (protocol != TEST_PROTOCOL && protocol != SUITE_PROTOCOL) return emptyList()
        val qualifiedName = toQualifiedName(path)
        val name = qualifiedName.substringAfterLast(NAME_SEPARATOR)
        return RsNamedElementIndex.findElementsByName(project, name, scope)
            .asSequence()
            .filter { it is RsFunction || it is RsMod }
            .filterIsInstance<RsQualifiedNamedElement>()
            .filter { it.qualifiedName == qualifiedName }
            .map { PsiLocation.fromPsiElement<PsiElement>(it) }
            .toList()
    }

    fun getTestFnUrl(name: String): String = "$TEST_PROTOCOL://$name"

    fun getTestFnUrl(function: RsFunction): String =
        getTestFnUrl(function.qualifiedName ?: "")

    fun getTestModUrl(name: String): String = "$SUITE_PROTOCOL://$name"

    fun getTestModUrl(mod: RsMod): String =
        getTestModUrl(mod.qualifiedName ?: "")

    private fun toQualifiedName(path: String): String {
        val targetName = path.substringBefore(NAME_SEPARATOR).substringBeforeLast("-")
        val qualifiedName = path.substringAfter(NAME_SEPARATOR)
        return "$targetName$NAME_SEPARATOR$qualifiedName"
    }
}
