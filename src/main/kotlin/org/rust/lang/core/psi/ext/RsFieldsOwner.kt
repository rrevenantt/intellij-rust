/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.RsBlockFields
import org.rust.lang.core.psi.RsNamedFieldDecl
import org.rust.lang.core.psi.RsTupleFieldDecl
import org.rust.lang.core.psi.RsTupleFields
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.type

interface RsFieldsOwner : RsElement, RsNameIdentifierOwner {
    val blockFields: RsBlockFields?
    val tupleFields: RsTupleFields?
}

val RsFieldsOwner.fields: List<RsFieldDecl>
    get() = namedFields + positionalFields

val RsFieldsOwner.namedFields: List<RsNamedFieldDecl>
    get() = blockFields?.namedFieldDeclList.orEmpty()

val RsFieldsOwner.positionalFields: List<RsTupleFieldDecl>
    get() = tupleFields?.tupleFieldDeclList.orEmpty()

/**
 * If some field of a struct/enum is private (not visible from [mod]),
 * it isn't possible to instantiate it at [mod] anyhow.
 * ```
 * mod foo {
 *     pub struct S {
 *         field: i32
 *     }
 * }
 * fn main() {
 *     let s = S { field: 0 } // Error: the field is private. Can't instantiate `S`
 * }
 * ```
 */
fun RsFieldsOwner.canBeInstantiatedIn(mod: RsMod): Boolean =
    namedFields.all { it.isVisibleFrom(mod) } && positionalFields.all { it.isVisibleFrom(mod) }

val RsFieldsOwner.fieldTypes: List<Ty>
    get() = blockFields?.namedFieldDeclList?.mapNotNull { it.typeReference?.type }
        ?: tupleFields?.tupleFieldDeclList?.mapNotNull { it.typeReference.type }.orEmpty()

/**
 * True for:
 * ```
 * struct S;
 * enum E { A }
 * ```
 * but false for:
 * ```
 * struct S {}
 * struct S();
 * ```
 */
val RsFieldsOwner.isFieldless: Boolean
    get() = blockFields == null && tupleFields == null

val RsFieldsOwner.size: Int
    get() = tupleFields?.tupleFieldDeclList?.size ?: blockFields?.namedFieldDeclList?.size ?: 0
