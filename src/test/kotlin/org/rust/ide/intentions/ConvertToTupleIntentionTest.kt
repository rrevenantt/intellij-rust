/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class ConvertToTupleIntentionTest : RsIntentionTestBase(ConvertToTupleIntention()) {

    fun `test simple`() = doAvailableTest("""
        struct Test{/*caret*/
            pub a:usize,
            b:i32
        }
    """, """
        struct Test(pub usize, i32);
    """)

    fun `test simple enum`() = doAvailableTest("""
        enum Test{
            A{ /*caret*/a:usize,
                b:i32 },
            B
        }
        fn main(){
            let Test::A{a,b} = Test::A {a:0,b:0};
        }
    """, """
        enum Test{
            A(usize, i32),
            B
        }
        fn main(){
            let Test::A(a, b) = Test::A(0, 0);
        }
    """)

    fun `test convert stuct literal`() = doAvailableTest("""
        struct Test{/*caret*/
            pub a:usize,
            b:i32
        }
        fn main (){
            let (a, b) = (0, 0);
            let x = Test{a: 0,b: 0};
            let x = Test{b: 1,a: 0};
            let x = Test{a, b};
            let a = 0;
            let x = Test{a, ..x };
        }
    """, """
        struct Test(pub usize, i32);
        fn main (){
            let (a, b) = (0, 0);
            let x = Test(0, 0);
            let x = Test(0, 1);
            let x = Test(a, b);
            let a = 0;
            let x = Test { 0: a, ..x };
        }
    """)

    fun `test convert field access`() = doAvailableTest("""
        struct Test{/*caret*/
            pub a:usize,
            b:i32
        }
        fn main (){
            let var = Test{a: 0, b: 0};
            let x = var.a;
            let x = var.b;
        }
    """, """
        struct Test(pub usize, i32);
        fn main (){
            let var = Test(0, 0);
            let x = var.0;
            let x = var.1;
        }
    """)

    fun `test convert destructuring`() = doAvailableTest("""
        struct Test{/*caret*/
            pub a:usize,
            b:i32
        }
        fn main (){
            let Test{a, b} = Test{a: 0, b: 0};
            let Test{ref a, b:mut var} = Test{a: 0, b: 0};
            let Test{a:var1, b:var2} = Test{a: 0, b: 0};
            let Test{a, ..} = Test{a: 0, b: 0};
            let Test{b:var, ..} = Test{a: 0, b: 0};
        }
    """, """
        struct Test(pub usize, i32);
        fn main (){
            let Test(a, b) = Test(0, 0);
            let Test(ref a, mut var) = Test(0, 0);
            let Test(var1, var2) = Test(0, 0);
            let Test(a, _) = Test(0, 0);
            let Test(_, var) = Test(0, 0);
        }
    """)

}
