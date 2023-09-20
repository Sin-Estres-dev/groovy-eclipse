/*
 * Copyright 2009-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.eclipse.jdt.core.groovy.tests.search;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.eclipse.jdt.core.tests.builder.Problem;
import org.eclipse.jdt.groovy.core.Activator;
import org.junit.Test;

/**
 * Tests for all Groovy 2.1 specific things for example, {@link groovy.lang.DelegatesTo}.
 */
public final class Groovy21InferencingTests extends InferencingTestSuite {

    @Test
    public void testDelegatesToValue1() {
        //@formatter:off
        String contents =
            "class Other { }\n" +
            "def meth(@DelegatesTo(Other) Closure c) { }\n" +
            "meth { delegate }";
        //@formatter:on
        assertType(contents, "delegate", "Other");
    }

    @Test
    public void testDelegatesToValue2() {
        //@formatter:off
        String contents =
            "class Other { }\n" +
            "def meth(@DelegatesTo(Other) c) { }\n" +
            "meth { delegate }";
        //@formatter:on
        assertType(contents, "delegate", "Other");
    }

    @Test
    public void testDelegatesToValue3() {
        //@formatter:off
        String contents =
            "class Other { int xxx }\n" +
            "def meth(@DelegatesTo(Other) Closure c) { }\n" +
            "meth { xxx }";
        //@formatter:on
        assertType(contents, "xxx", "java.lang.Integer");
    }

    @Test
    public void testDelegatesToValue4() {
        //@formatter:off
        String contents =
            "def meth(@DelegatesTo(List) Closure c) { }\n" +
            "meth { delegate }";
        //@formatter:on
        assertType(contents, "delegate", "java.util.List");
    }

    @Test
    public void testDelegatesToValue5() {
        //@formatter:off
        String contents =
            "def meth(int x, int y, @DelegatesTo(List) Closure c) { }\n" +
            "meth 1, 2, { delegate }";
        //@formatter:on
        assertType(contents, "delegate", "java.util.List");
    }

    @Test // expected to be broken (due to missing closing angle bracket on type)
    public void testDelegatesToValue6() {
        //@formatter:off
        String contents =
            "def meth(int x, int y, @DelegatesTo(List<String) Closure c) { }\n" +
            "meth { delegate }";
        //@formatter:on
        assertType(contents, "delegate", DEFAULT_UNIT_NAME);
    }

    @Test
    public void testDelegatesToTarget1() {
        createUnit("C", "class C { static def cat(\n" +
            "@DelegatesTo.Target Object self, @DelegatesTo(strategy=Closure.DELEGATE_FIRST) Closure code) {}\n}");
        //@formatter:off
        String contents =
            "class A { def x }\n" +
            "class B { def x, y\n" +
            "  def m(A a) {\n" +
            "    use (C) {\n" +
            "      a.cat {\n" + // delegate is A, owner is B
            "        x\n" +
            "        y\n" +
            "      }\n" +
            "    }\n" +
            "  }\n" +
            "}";
        //@formatter:on
        assertDeclaringType(contents, "x", "A");
        assertDeclaringType(contents, "y", "B");
    }

    @Test
    public void testDelegatesToTarget2() {
        createUnit("C", "class C { static def cat(\n" +
            "@DelegatesTo.Target('self') Object self, @DelegatesTo(target='self', strategy=Closure.DELEGATE_FIRST) Closure code) {}\n}");
        //@formatter:off
        String contents =
            "class A { def x }\n" +
            "class B { def x, y\n" +
            "  def m(A a) {\n" +
            "    use (C) {\n" +
            "      a.cat {\n" + // delegate is A, owner is B
            "        x\n" +
            "        y\n" +
            "      }\n" +
            "    }\n" +
            "  }\n" +
            "}";
        //@formatter:on
        assertDeclaringType(contents, "x", "A");
        assertDeclaringType(contents, "y", "B");
    }

    @Test // uses constant instead of literal for target
    public void testDelegatesToTarget3() {
        createUnit("C",
            "class C {\n" +
            "  private static final String SELF = 'self'\n" +
            "  static def cat(\n" +
            "    @DelegatesTo.Target(C.SELF) Object self,\n" + // getText() will not work with qualifier
            "    @DelegatesTo(target=SELF, strategy=Closure.DELEGATE_FIRST) Closure code\n" +
            "   ) {}\n" +
            "}");
        //@formatter:off
        String contents =
            "class A { def x }\n" +
            "class B { def x, y\n" +
            "  def m(A a) {\n" +
            "    use (C) {\n" +
            "      a.cat {\n" + // delegate is A, owner is B
            "        x\n" +
            "        y\n" +
            "      }\n" +
            "    }\n" +
            "  }\n" +
            "}";
        //@formatter:on
        assertDeclaringType(contents, "x", "A");
        assertDeclaringType(contents, "y", "B");
    }

    @Test
    public void testDelegatesToTarget4() {
        createUnit("C", "class C { static def cat(\n" +
            "@DelegatesTo.Target('self') Object self, @DelegatesTo(target='self', strategy=Closure.DELEGATE_ONLY) Closure code) {}\n}");
        //@formatter:off
        String contents =
            "class A { def x }\n" +
            "class B { def x, y\n" +
            "  def m(A a) {\n" +
            "    use (C) {\n" +
            "      a.cat {\n" + // delegate is A, owner is B
            "        x\n" +
            "        y\n" +
            "      }\n" +
            "    }\n" +
            "  }\n" +
            "}";
        //@formatter:on
        assertDeclaringType(contents, "x", "A");
        int offset = contents.lastIndexOf('y');
        assertUnknownConfidence(contents, offset, offset + 1);
    }

    @Test
    public void testDelegatesToTarget5() {
        createUnit("C", "class C { static def cat(\n" +
            "@DelegatesTo.Target('self') Object self, @DelegatesTo(target='self', strategy=Closure.OWNER_FIRST) Closure code) {}\n}");
        //@formatter:off
        String contents =
            "class A { def x, z }\n" +
            "class B { def x, y\n" +
            "  def m(A a) {\n" +
            "    use (C) {\n" +
            "      a.cat {\n" + // delegate is A, owner is B
            "        x\n" +
            "        y\n" +
            "        z\n" +
            "      }\n" +
            "    }\n" +
            "  }\n" +
            "}";
        //@formatter:on
        assertDeclaringType(contents, "x", "B");
        assertDeclaringType(contents, "y", "B");
        assertDeclaringType(contents, "z", "A");
    }

    @Test
    public void testDelegatesToTarget6() {
        createUnit("C", "class C { static def cat(\n" +
            "@DelegatesTo.Target('self') Object self, @DelegatesTo(target='self', strategy=Closure.OWNER_ONLY) Closure code) {}\n}");
        //@formatter:off
        String contents =
            "class A { def x }\n" +
            "class B { def x, y\n" +
            "  def m(A a) {\n" +
            "    use (C) {\n" +
            "      a.cat {\n" + // delegate is A, owner is B
            "        x\n" +
            "        y\n" +
            "      }\n" +
            "    }\n" +
            "  }\n" +
            "}";
        //@formatter:on
        assertDeclaringType(contents, "x", "B");
        assertDeclaringType(contents, "y", "B");
    }

    @Test // seemingly invalid combination
    public void testDelegatesToTarget7() {
        createUnit("C", "class C { static def cat(\n" +
            "@DelegatesTo.Target('self') Object self, @DelegatesTo(target='self', strategy=Closure.TO_SELF) Closure code) {}\n}");
        //@formatter:off
        String contents =
            "class A { def x }\n" +
            "class B { def x, y\n" +
            "  def m(A a) {\n" +
            "    use (C) {\n" +
            "      a.cat {\n" + // delegate is A, owner is B
            "        x\n" +
            "        y\n" +
            "      }\n" +
            "    }\n" +
            "  }\n" +
            "}";
        //@formatter:on
        int offset = contents.lastIndexOf('x');
        assertUnknownConfidence(contents, offset, offset + 1);
        offset = contents.lastIndexOf('y');
        assertUnknownConfidence(contents, offset, offset + 1);
    }

    @Test // https://github.com/groovy/groovy-eclipse/issues/1147
    public void testDelegatesToTarget8() {
        //@formatter:off
        String contents =
            "abstract class A {\n" +
            "  def x\n" +
            "  public <T> void with(\n" +
            "    @DelegatesTo.Target T self,\n" +
            "    @DelegatesTo(strategy=Closure.DELEGATE_FIRST) Closure code) {\n" +
            "  }\n" +
            "}\n" +
            "class B extends A {\n" +
            "  def x, y\n" +
            "  def m() {\n" +
            "    def a = new A() {}\n" +
            "    with(a) { ->\n" + // delegate is A, owner is B
            "      x\n" +
            "      y\n" +
            "    }\n" +
            "  }\n" +
            "}";
        //@formatter:on
        assertDeclaringType(contents, "x", "A");
        assertDeclaringType(contents, "y", "B");
    }

    @Test // https://github.com/groovy/groovy-eclipse/issues/415
    public void testDelegatesToTypeName1() {
        //@formatter:off
        String contents =
            "def meth(int x, int y, @DelegatesTo(type='java.util.List') Closure block) { }\n" +
            "meth 1, 2, { delegate }";
        //@formatter:on
        assertType(contents, "delegate", "java.util.List");
    }

    @Test // https://github.com/groovy/groovy-eclipse/issues/415
    public void testDelegatesToTypeName2() {
        //@formatter:off
        String contents =
            "class C {\n" +
            "  private static final String LIST = 'java.util.List'\n" +
            "  static void meth(int x, int y, @DelegatesTo(type=LIST) Closure block) { }\n" +
            "}\n" +
            "C.meth 1, 2, { delegate }";
        //@formatter:on
        assertType(contents, "delegate", "java.util.List");
    }

    @Test // https://github.com/groovy/groovy-eclipse/issues/966
    public void testDelegatesToTypeName3() {
        //@formatter:off
        createUnit("p", "A",
            "package p\n" +
            "class A {\n" +
            "  static class B {\n" +
            "    Number getNumber() {}\n" +
            "  }\n" +
            "}\n");
        //@formatter:on
        incrementalBuild();

        //@formatter:off
        String contents =
            "class C {\n" +
            "  static void meth(@DelegatesTo(type='p.A.B') Closure block) { }\n" +
            "}\n" +
            "C.meth { number }";
        //@formatter:on
        assertType(contents, "number", "java.lang.Number");
    }

    @Test // https://github.com/groovy/groovy-eclipse/issues/966
    public void testDelegatesToTypeName4() {
        //@formatter:off
        createUnit("p", "A",
            "package p\n" +
            "class A {\n" +
            "  static class B {\n" +
            "    Number getNumber() {}\n" +
            "  }\n" +
            "}\n");
        //@formatter:on
        incrementalBuild();

        //@formatter:off
        String contents =
            "class C {\n" +
            "  static void meth(@DelegatesTo(type='p.A$B') Closure block) { }\n" + // uses '$' instead of '.'
            "}\n" +
            "C.meth { number }";
        //@formatter:on
        assertType(contents, "number", "java.lang.Number");
    }

    @Test // https://github.com/groovy/groovy-eclipse/issues/966
    public void testDelegatesToTypeName5() {
        //@formatter:off
        createUnit("p", "A",
            "package p\n" +
            "class A {\n" +
            "  private static class B {\n" +
            "    Number getNumber() {}\n" +
            "  }\n" +
            "  void impl(@DelegatesTo(B) Closure c) {\n" +
            "    c.setDelegate(new B())\n" +
            "    c.call((Object)null)\n" +
            "  }\n" +
            "}\n");
        //@formatter:on
        incrementalBuild();

        //@formatter:off
        String contents =
            "class C {\n" +
            "  static void meth(@DelegatesTo(type='p.A.B') Closure block) { new A().impl(block) }\n" +
            "}\n" +
            "C.meth { number }";
        //@formatter:on
        assertType(contents, "number", "java.lang.Number");
    }

    @Test // https://issues.apache.org/jira/browse/GROOVY-11168
    public void testDelegatesToTypeName6() {
        //@formatter:off
        String contents =
            "def <T> T m(int i, @DelegatesTo(type='T') Closure block) { }\n" +
            "this.<String>m(2) { delegate }";
        //@formatter:on
        assertType(contents, "delegate", "java.lang.String");
    }

    @Test
    public void testDelegatesToResolveStrategy1() {
        //@formatter:off
        String contents =
            "class A {}\n" +
            "class B { \n" +
            "  def m(@DelegatesTo(value=A, strategy=Closure.OWNER_ONLY) Closure code) {\n" +
            "  }\n" +
            "  def x() {\n" +
            "    m {" + // delegate is A, owner is B
            "      delegate\n" +
            "      owner\n" +
            "    }\n" +
            "  }\n" +
            "}";
        //@formatter:on
        assertType(contents, "delegate", "A");
        assertType(contents, "owner", "B");
    }

    @Test // https://github.com/groovy/groovy-eclipse/issues/657
    public void testDelegatesToResolveStrategy2() {
        //@formatter:off
        String contents =
            "class A {}\n" +
            "class B { \n" +
            "  def m(@DelegatesTo(value=A, strategy=Closure.DELEGATE_ONLY) Closure code) {\n" +
            "  }\n" +
            "  def x() {\n" +
            "    m {" + // delegate is A, owner is B
            "      delegate\n" +
            "      owner\n" +
            "    }\n" +
            "  }\n" +
            "}";
        //@formatter:on
        assertType(contents, "delegate", "A");
        assertType(contents, "owner", "B");
    }

    @Test
    public void testDelegatesToResolveStrategy3() {
        //@formatter:off
        String contents =
            "class A {}\n" +
            "class B { \n" +
            "  def m(@DelegatesTo(value=A, strategy=Closure.TO_SELF) Closure code) {\n" +
            "  }\n" +
            "  def x() {\n" +
            "    m {" + // delegate is A, owner is B
            "      delegate\n" +
            "      owner\n" +
            "    }\n" +
            "  }\n" +
            "}";
        //@formatter:on
        assertType(contents, "delegate", "A");
        assertType(contents, "owner", "B");
    }

    @Test // https://github.com/groovy/groovy-eclipse/issues/389
    public void testEnumOverrides1() {
        //@formatter:off
        String contents =
            "enum E {\n" +
            "  ONE() {\n" +
            "    void meth(Number param) { println param }\n" +
            "  },\n" +
            "  TWO() {\n" +
            "    void meth(Number param) { null }\n" +
            "  }\n" +
            "  abstract void meth(Number param);\n" +
            "}";
        //@formatter:on
        int offset = contents.indexOf("println param") + "println ".length();
        assertType(contents, offset, offset + "param".length(), "java.lang.Number");
    }

    @Test // https://github.com/groovy/groovy-eclipse/issues/390
    public void testEnumOverrides2() {
        //@formatter:off
        String contents =
            "@groovy.transform.CompileStatic\n" +
            "enum E {\n" +
            "  ONE() {\n" +
            "    void meth(Number param) { println param }\n" +
            "  },\n" +
            "  TWO() {\n" +
            "    void meth(Number param) { null }\n" +
            "  }\n" +
            "  abstract void meth(Number param);\n" +
            "}";
        //@formatter:on
        int offset = contents.indexOf("println param") + "println ".length();
        assertType(contents, offset, offset + "param".length(), "java.lang.Number");
    }

    @Test // https://github.com/groovy/groovy-eclipse/issues/1099
    public void testEnumOverrides3() {
        //@formatter:off
        String contents =
            "class C {\n" +
            "  enum E {\n" +
            "    ONE() {\n" +
            "      void meth(Number param) { helper() }\n" +
            "    },\n" +
            "    TWO() {\n" +
            "      void meth(Number param) { helper() }\n" +
            "    }\n" +
            "    abstract void meth(Number param)\n" +
            "    private static Number helper() {\n" +
            "      def xxx = 42" +
            "    }\n" +
            "  }\n" +
            "}";
        //@formatter:on
        int offset = contents.indexOf("helper");
        assertType(contents, offset, offset + "helper".length(), "java.lang.Number");

        assertType(contents, "xxx", "java.lang.Integer");
    }

    @Test // https://github.com/groovy/groovy-eclipse/issues/1100
    public void testEnumOverrides4() {
        //@formatter:off
        String contents =
            "class C {\n" +
            "  enum E {\n" +
            "    X {\n" +
            "      void meth() { helper() }\n" +
            "      private char helper() {}\n" +
            "    }\n" +
            "    abstract void meth()\n" +
            "  }\n" +
            "}";
        //@formatter:on
        int offset = contents.indexOf("helper");
        assertType(contents, offset, offset + "helper".length(), "java.lang.Character");
    }

    @Test // https://github.com/groovy/groovy-eclipse/issues/1100
    public void testEnumOverrides5() {
        //@formatter:off
        String contents =
            "@groovy.transform.CompileStatic\n" +
            "class C {\n" +
            "  @groovy.transform.CompileDynamic\n" +
            "  enum E {\n" +
            "    X {\n" +
            "      void meth() { helper() }\n" +
            "      private char helper() {}\n" +
            "    }\n" +
            "    abstract void meth()\n" +
            "  }\n" +
            "}";
        //@formatter:on
        int offset = contents.indexOf("helper");
        assertType(contents, offset, offset + "helper".length(), "java.lang.Character");
    }

    @Test // https://github.com/groovy/groovy-eclipse/issues/1100
    public void testEnumOverrides6() {
        //@formatter:off
        String contents =
            "@groovy.transform.CompileStatic\n" +
            "class C {\n" +
            "  enum E {\n" +
            "    X {\n" +
            "      void meth() { helper() }\n" +
            "      private char helper() {}\n" +
            "    }\n" +
            "    abstract void meth()\n" +
            "  }\n" +
            "}";
        //@formatter:on
        int offset = contents.indexOf("helper");
        assertType(contents, offset, offset + "helper".length(), "java.lang.Character");
    }

    @Test
    public void testTypeCheckingExtension() {
        Activator.getInstancePreferences().getBoolean(Activator.GROOVY_SCRIPT_FILTERS_ENABLED, Activator.DEFAULT_SCRIPT_FILTERS_ENABLED);
        Activator.getInstancePreferences().get(Activator.GROOVY_SCRIPT_FILTERS, Activator.DEFAULT_GROOVY_SCRIPT_FILTER);
        try {
            //@formatter:off
            createUnit("robot", "RobotMove",
                "package robot\n" +
                "import org.codehaus.groovy.ast.expr.MethodCall\n" +
                "import org.codehaus.groovy.ast.expr.VariableExpression\n" +
                "unresolvedVariable { VariableExpression var ->\n" +
                "    if ('robot' == var.name) {\n" +
                "        def robotClass = context.source.AST.classes.find { it.name == 'Robot' }\n" +
                "        storeType(var, robotClass)\n" +
                "        handled = true\n" +
                "    }\n" +
                "}\n" +
                "afterMethodCall { MethodCall mc ->\n" +
                "    def method = getTargetMethod(mc)\n" +
                "    if (mc.objectExpression.name == 'robot' && method.name == 'move') {\n" +
                "        def args = getArguments(mc)\n" +
                "        if (args && isConstantExpression(args[0]) && args[0].value instanceof String) {\n" +
                "            def content = args[0].text\n" +
                "            if (!(content in ['left', 'right', 'backward', 'forward'])) {\n" +
                "                addStaticTypeError(\"'${content}' is not a valid direction\", args[0])\n" +
                "            }\n" +
                "        }\n" +
                "    }\n" +
                "}");
            //@formatter:on

            // set the script folders
            Activator.getInstancePreferences().putBoolean(Activator.GROOVY_SCRIPT_FILTERS_ENABLED, true);
            Activator.getInstancePreferences().put(Activator.GROOVY_SCRIPT_FILTERS, "src/robot/*Move.groovy,y");

            incrementalBuild();

            //@formatter:off
            String contents =
                "import groovy.transform.TypeChecked\n" +
                "class Robot {\n" +
                "    void move(String dist) { println \"Moved $dist\" }\n" +
                "}\n" +
                "@TypeChecked(extensions = 'robot/RobotMove.groovy')\n" +
                "void operate() {\n" +
                "    robot.move \"left\"\n" +
                "}";
            //@formatter:on

            assertType(contents, "robot", "Robot");
            assertType(contents, "move", "java.lang.Void");

            // ensure there aren't build problems
            incrementalBuild(project.getFullPath());
            Problem[] problems = env.getProblemsFor(project.getFullPath());
            assertEquals("Should have found no problems in:\n" + Arrays.toString(problems), 0, problems.length);
        } finally {
            Activator.getInstancePreferences().putBoolean(Activator.GROOVY_SCRIPT_FILTERS_ENABLED, Activator.DEFAULT_SCRIPT_FILTERS_ENABLED);
            Activator.getInstancePreferences().put(Activator.GROOVY_SCRIPT_FILTERS, Activator.DEFAULT_GROOVY_SCRIPT_FILTER);
        }
    }
}
