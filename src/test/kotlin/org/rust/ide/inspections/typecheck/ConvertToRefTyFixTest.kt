/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.typecheck


class ConvertToRefTyFixTest : ConvertToTyUsingTraitFixTestBase(false, "AsRef", "as_ref")