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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.codehaus.jdt.groovy.model.GroovyCompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchPattern;
import org.junit.Test;

/**
 * Tests the groovy-specific type referencing search support.
 */
public final class TypeReferenceSearchTests extends SearchTestSuite {

    @Test
    public void testSearchForTypesScript1() throws Exception {
        doTestForTwoInScript("First f = new First()");
    }

    @Test
    public void testSearchForTypesScript2() throws Exception {
        doTestForTwoInScript("First.class\nFirst.class");
    }

    @Test
    public void testSearchForTypesScript3() throws Exception {
        doTestForTwoInScript("[First, First]");
    }

    @Test
    public void testSearchForTypesScript4() throws Exception {
        // the key of a map is interpreted as a string
        // so don't put 'First' there
        doTestForTwoInScript("def x = [a : First]\nx[First.class]");
    }

    @Test
    public void testSearchForTypesScript5() throws Exception {
        doTestForTwoInScript("x(First, First.class)");
    }

    @Test
    public void testSearchForTypesScript6() throws Exception {
        // note that in "new First[ new First() ]", the first 'new First' is removed
        // by the AntlrPluginParser and so there is no way to search for it
        //doTestForTwoInScript("new First[ new First() ]");
        doTestForTwoInScript("[ new First(), First ]");
    }

    @Test
    public void testSearchForTypesClosure1() throws Exception {
        doTestForTwoInScript("{ First first, First second -> ;}");
    }

    @Test
    public void testSearchForTypesClosure2() throws Exception {
        doTestForTwoInScript("def x = {\n First first = new First()\n}");
    }

    @Test
    public void testSearchForTypesClosure3() throws Exception {
        doTestForTwoInScript("def x = {\n First.class\n First.class\n}");
    }

    @Test
    public void testSearchForTypesClass1() throws Exception {
        doTestForTwoInClass("class Second extends First {\n First x\n}");
    }

    @Test
    public void testSearchForTypesClass2() throws Exception {
        doTestForTwoInClass("class Second extends First { First x() {}\n}");
    }

    @Test
    public void testSearchForTypesClass3() throws Exception {
        doTestForTwoInClass("class Second extends First { def x(First y) {}\n}");
    }

    @Test
    public void testSearchForTypesClass4() throws Exception {
        doTestForTwoInClass("class Second extends First { def x(First ... y) {}\n}");
    }

    @Test
    public void testSearchForTypesClass5() throws Exception {
        doTestForTwoInClass("class Second extends First { def x(y = new First()) {}\n}");
    }

    @Test
    public void testSearchForTypesClass6() throws Exception {
        doTestForTwoTypeReferences("class First {}", "class Second implements First { def x(First y) {}\n}", false, 0);
    }

    @Test
    public void testSearchForTypesClass7() throws Exception {
        createUnit("other", "First", "class First {}");
        doTestForTwoInClass("class Second extends First {\n" + // yes
            "  def x() {\n" +
            "    y = new other.First()\n" + // no
            "    y = new First()\n" + // yes
            "  }\n" +
            "}\n");
    }

    @Test
    public void testSearchForTypesArray1() throws Exception {
        doTestForTwoInScript("First[] f = {\n First[] h -> h\n}");
    }

    @Test // GRECLIPSE-650
    public void testFindClassDeclaration() throws Exception {
        String firstContents = "class First {\n First x\n}";
        String secondContents = "class Second extends First {}";
        List<SearchMatch> matches = searchForFirst(firstContents, secondContents, "", "");
        assertEquals("Should find First 2 times", 2, matches.size());

        SearchMatch match = matches.get(0);
        int start = match.getOffset();
        int end = start + match.getLength();
        assertEquals("Invalid location", "First", firstContents.substring(start, end));

        match = matches.get(1);
        start = match.getOffset();
        end = start + match.getLength();
        assertEquals("Invalid location", "First", secondContents.substring(start, end));
    }

    /**
     * Ensures that queries for some type declaration with a pattern like '*Tests' works.
     */
    @Test
    public void testFindClassDeclarationWithPattern() throws Exception {
        GroovyCompilationUnit songTests = createUnit("gtunes", "SongTests",
            "package gtunes\n" +
            "\n" +
            "final class SongTests {" +
            "  def testSomething() {\n" +
            "    println 'testing'\n" +
            "  }\n" +
            "}");
        GroovyCompilationUnit weirdTests = createUnit("gtunes", "Song2tests",
            "package gtunes\n" +
            "\n" +
            "class Song2tests {" +
            "  SongTests theOtherTests\n" + // shouldn't find
            "  def testSomethingElse() {\n" +
            "    println 'testing'\n" +
            "  }\n" +
            "}");
        GroovyCompilationUnit artistTests = createUnit("gtunes", "ArtistTests",
            "package gtunes\n" +
            "\n" +
            "final class ArtistTests {" +
            "  def testSomething() {\n" +
            "    println 'testing'\n" +
            "  }\n" +
            "}");
        IType songTestsType = findType("SongTests", songTests);
        assertNotNull(songTestsType);
        IType artistTestsType = findType("ArtistTests", artistTests);
        assertNotNull(artistTestsType);

        IJavaElement[] searchScope = {songTests, weirdTests, artistTests};

        List<SearchMatch> searchResult = search(
            SearchPattern.createPattern("*Tests",
                IJavaSearchConstants.TYPE, IJavaSearchConstants.DECLARATIONS,
                SearchPattern.R_EXACT_MATCH | SearchPattern.R_CASE_SENSITIVE),
            SearchEngine.createJavaSearchScope(searchScope, IJavaSearchScope.SOURCES));

        assertEquals(2, searchResult.size());
        assertTrue(searchResult.stream().map(SearchMatch::getElement).anyMatch(songTestsType::equals));
        assertTrue(searchResult.stream().map(SearchMatch::getElement).anyMatch(artistTestsType::equals));
    }

    @Test // GRECLIPSE-628
    public void testShouldntFindClassDeclarationInScript() throws Exception {
        String firstContents = "print 'me'";
        String secondContents = "print 'me'";
        List<SearchMatch> matches = searchForFirst(firstContents, secondContents, "", "");
        assertEquals("Should find no matches", 0, matches.size());
    }

    @Test
    public void testImport1() throws Exception {
        String secondContents = "import First\n new First()";

        List<SearchMatch> matches = searchForFirst("class First {}", secondContents, "", "");
        assertEquals(2, matches.size());

        SearchMatch match = matches.get(0);
        assertEquals("First".length(), match.getLength());
        assertEquals(secondContents.indexOf("First"), match.getOffset());

        match = matches.get(1);
        assertEquals("First".length(), match.getLength());
        assertEquals(secondContents.lastIndexOf("First"), match.getOffset());
    }

    @Test
    public void testImport2() throws Exception {
        String secondContents = "import First as Alpha\n new Alpha()";

        List<SearchMatch> matches = searchForFirst("class First {}", secondContents, "", "");
        assertEquals(1, matches.size());

        SearchMatch match = matches.get(0);
        assertEquals("First".length(), match.getLength());
        assertEquals(secondContents.indexOf("First"), match.getOffset());
    }

    @Test
    public void testImport3() throws Exception {
        String secondContents = "package q\n import p.*\n First.class";

        List<SearchMatch> matches = searchForFirst("package p\nclass First {}", secondContents, "p", "q");
        assertEquals(1, matches.size());

        SearchMatch match = matches.get(0);
        assertEquals("First".length(), match.getLength());
        assertEquals(secondContents.indexOf("First"), match.getOffset());
    }

    @Test
    public void testNoImport() throws Exception {
        String secondContents = "package q\n p.First.class";

        List<SearchMatch> matches = searchForFirst("package p\nclass First {}", secondContents, "p", "q");
        assertEquals(1, matches.size());

        SearchMatch match = matches.get(0);
        assertEquals("p.First".length(), match.getLength());
        assertEquals(secondContents.indexOf("p.First"), match.getOffset());
    }

    @Test // https://github.com/groovy/groovy-eclipse/issues/468
    public void testCoercion1() throws Exception {
        String firstContents =
            "package p\n" +
            "interface First {\n" +
            "  void meth();\n" +
            "}\n";
        String secondContents =
            "package p\n" +
            "class Second {\n" +
            "  def m() {\n" +
            "    return {->\n" +
            "    } as First\n" +
            "  }\n" +
            "}\n";

        List<SearchMatch> matches = searchForFirst(firstContents, secondContents, "p", "p");
        assertEquals(1, matches.size());

        SearchMatch match = matches.get(0);
        assertEquals("First".length(), match.getLength());
        assertEquals(secondContents.indexOf("First"), match.getOffset());
    }

    @Test // https://github.com/groovy/groovy-eclipse/issues/442
    public void testGenerics1() throws Exception {
        String firstContents =
            "package p\n" +
            "class First {\n" +
            "}\n";
        String secondContents =
            "package p\n" +
            "class Second {\n" +
            "  List<First> firsts\n" +
            "}\n";

        List<SearchMatch> matches = searchForFirst(firstContents, secondContents, "p", "p");
        assertEquals(1, matches.size());

        SearchMatch match = matches.get(0);
        assertEquals("First".length(), match.getLength());
        assertEquals(secondContents.indexOf("First"), match.getOffset());
    }

    @Test
    public void testGenerics2() throws Exception {
        String firstContents =
            "package p\n" +
            "class First {\n" +
            "}\n";
        String secondContents =
            "package p\n" +
            "class Second<T extends First> {\n" +
            "}\n";

        List<SearchMatch> matches = searchForFirst(firstContents, secondContents, "p", "p");
        assertEquals(1, matches.size());

        SearchMatch match = matches.get(0);
        assertEquals("First".length(), match.getLength());
        assertEquals(secondContents.indexOf("First"), match.getOffset());
    }

    @Test
    public void testInnerTypes1() throws Exception {
        String firstContents =
            "class Other {\n" +
            "  class First {}\n" +
            "}\n";
        String secondContents =
            "import Other.First\n" +
            "Map<First, ? extends First> h\n" +
            "Other.First j\n" +
            "First i\n";

        String name = "First";
        int len = name.length();

        List<SearchMatch> matches = searchForFirst(firstContents, secondContents, "", "");
        assertEquals("Wrong number of matches found:\n" + matches, 5, matches.size());

        int start = secondContents.indexOf("First");
        SearchMatch match = matches.get(0);
        assertEquals("Wrong offset " + match, start, match.getOffset());
        assertEquals("Wrong length " + match, len, match.getLength());

        start = secondContents.indexOf("First", start + 1);
        match = matches.get(1);
        assertEquals("Wrong offset " + match, start, match.getOffset());
        assertEquals("Wrong length " + match, len, match.getLength());

        start = secondContents.indexOf("First", start + 1);
        match = matches.get(2);
        assertEquals("Wrong offset " + match, start, match.getOffset());
        assertEquals("Wrong length " + match, len, match.getLength());

        start = secondContents.indexOf("First", start + 1);
        match = matches.get(3);
        assertEquals("Wrong offset " + match, start, match.getOffset());
        assertEquals("Wrong length " + match, len, match.getLength());

        start = secondContents.indexOf("First", start + 1);
        match = matches.get(4);
        assertEquals("Wrong offset " + match, start, match.getOffset());
        assertEquals("Wrong length " + match, len, match.getLength());
    }

    @Test
    public void testInnerTypes2() throws Exception {
        String firstContents =
            "package p\n" +
            "class Other {\n" +
            "  class First {}\n" +
            "}\n";
        String secondContents =
            "package q\n" +
            "import p.Other.First\n" +
            "Map<First, ? extends First> h\n" +
            "p.Other.First j\n" +
            "First i\n";

        String name = "First";
        int len = name.length();

        List<SearchMatch> matches = searchForFirst(firstContents, secondContents, "p", "q");
        assertEquals("Wrong number of matches found:\n" + matches, 5, matches.size());

        int start = secondContents.indexOf("First");
        SearchMatch match = matches.get(0);
        assertEquals("Wrong offset " + match, start, match.getOffset());
        assertEquals("Wrong length " + match, len, match.getLength());

        start = secondContents.indexOf("First", start + 1);
        match = matches.get(1);
        assertEquals("Wrong offset " + match, start, match.getOffset());
        assertEquals("Wrong length " + match, len, match.getLength());

        start = secondContents.indexOf("First", start + 1);
        match = matches.get(2);
        assertEquals("Wrong offset " + match, start, match.getOffset());
        assertEquals("Wrong length " + match, len, match.getLength());

        start = secondContents.indexOf("First", start + 1);
        match = matches.get(3);
        assertEquals("Wrong offset " + match, start, match.getOffset());
        assertEquals("Wrong length " + match, len, match.getLength());

        start = secondContents.indexOf("First", start + 1);
        match = matches.get(4);
        assertEquals("Wrong offset " + match, start, match.getOffset());
        assertEquals("Wrong length " + match, len, match.getLength());
    }

    @Test
    public void testInnerTypes3() throws Exception {
        String firstContents =
            "package p\n" +
            "class Other {\n" +
            "  class First {}\n" +
            "}\n";
        String secondContents =
            "package q\n" +
            "import p.Other\n" +
            "Map<Other.First, ? extends Other.First> h\n" +
            "p.Other.First j\n" +
            "Other.First i\n";

        String name = "First";
        int len = name.length();

        List<SearchMatch> matches = searchForFirst(firstContents, secondContents, "p", "q");
        assertEquals("Wrong number of matches found:\n" + matches, 4, matches.size());

        int start = secondContents.indexOf("First");
        SearchMatch match = matches.get(0);
        assertEquals("Wrong offset " + match, start, match.getOffset());
        assertEquals("Wrong length " + match, len, match.getLength());

        start = secondContents.indexOf("First", start + 1);
        match = matches.get(1);
        assertEquals("Wrong offset " + match, start, match.getOffset());
        assertEquals("Wrong length " + match, len, match.getLength());

        start = secondContents.indexOf("First", start + 1);
        match = matches.get(2);
        assertEquals("Wrong offset " + match, start, match.getOffset());
        assertEquals("Wrong length " + match, len, match.getLength());

        start = secondContents.indexOf("First", start + 1);
        match = matches.get(3);
        assertEquals("Wrong offset " + match, start, match.getOffset());
        assertEquals("Wrong length " + match, len, match.getLength());
    }

    @Test
    public void testInnerTypes4() throws Exception {
        String firstContents =
            "package p\n" +
            "class Other {\n" +
            "  static class First {}\n" +
            "}\n";
        String secondContents =
            "package q\n" +
            "import p.*\n" +
            "new Other.First()" +
            "new p.Other.First()\n";

        List<SearchMatch> matches = searchForFirst(firstContents, secondContents, "p", "q");
        assertEquals(2, matches.size());

        SearchMatch match = matches.get(0);
        assertEquals("First".length(), match.getLength());
        assertEquals(secondContents.indexOf("First"), match.getOffset());

        match = matches.get(1);
        assertEquals("First".length(), match.getLength());
        assertEquals(secondContents.lastIndexOf("First"), match.getOffset());
    }

    @Test
    public void testInnerTypes5() throws Exception {
        String firstContents =
            "package p\n" +
            "class First {\n" +
            "  static class Other {}\n" +
            "}\n";
        String secondContents =
            "package q\n" +
            "import p.*\n" +
            "new First.Other()\n" +
            "new p.First.Other()\n";

        List<SearchMatch> matches = searchForFirst(firstContents, secondContents, "p", "q");
        assertEquals(2, matches.size());

        SearchMatch match = matches.get(0);
        assertEquals("First".length(), match.getLength());
        assertEquals(secondContents.indexOf("First"), match.getOffset());

        match = matches.get(1);
        assertEquals("p.First".length(), match.getLength());
        assertEquals(secondContents.lastIndexOf("p.First"), match.getOffset());
    }

    @Test
    public void testConstructorWithDefaultArgsInCompileStatic() throws Exception {
        String firstContents =
                "package p\n" +
                "class First {\n" +
                "  Class name\n" +
                "}\n";
        String secondContents =
                "package q\n" +
                "import p.*\n" +
                "import groovy.transform.CompileStatic\n" +
                "\n" +
                "@CompileStatic\n" +
                "class Foo {\n" +
                "  void apply() {\n" +
                "    new First([name: First])\n" +
                "  }\n" +
                "}\n";

        List<SearchMatch> matches = searchForFirst(firstContents, secondContents, "p", "q");
        int lastMatch = 0;
        for (SearchMatch searchMatch : matches) {
            int start = secondContents.indexOf("First", lastMatch);
            assertEquals("Wrong offset " + searchMatch, start, searchMatch.getOffset());
            assertEquals("Wrong length " + searchMatch, "First".length(), searchMatch.getLength());
            lastMatch = start + 1;
        }
        assertEquals("Wrong number of matches found\n" + matches, 2, matches.size());
    }

    //--------------------------------------------------------------------------

    private void doTestForTwoInClass(String secondContents) throws Exception {
        doTestForTwoTypeReferences("class First {}", secondContents, false, 0);
    }

    private void doTestForTwoInScript(String secondContents) throws Exception {
        doTestForTwoTypeReferences("class First {}", secondContents, true, 3);
    }

    private void doTestForTwoTypeReferences(String firstContents, String secondContents, boolean secondIsScript, int childIndex) throws Exception {
        String firstClassName = "First";
        String secondClassName = "Second";
        var firstUnit = createUnit(firstClassName, firstContents);
        var secondUnit = createUnit(secondClassName, secondContents);

        IJavaElement firstMatchEnclosingElement;
        IJavaElement secondMatchEnclosingElement;
        if (secondIsScript) {
            firstMatchEnclosingElement = findType(secondClassName, secondUnit).getChildren()[childIndex];
        } else {
            // if not a script, then the first match is always enclosed in the type
            firstMatchEnclosingElement = findType(secondClassName, secondUnit);
        }
        // match is enclosed in run method (for script), or nth method for class
        secondMatchEnclosingElement = findType(secondClassName, secondUnit).getChildren()[childIndex];

        IType firstType = findType(firstClassName, firstUnit);
        SearchPattern pattern = SearchPattern.createPattern(firstType, IJavaSearchConstants.REFERENCES);
        checkMatches(secondContents, firstClassName, pattern, secondUnit, firstMatchEnclosingElement, secondMatchEnclosingElement);
    }

    private List<SearchMatch> searchForFirst(String firstContents, String secondContents, String firstPackageName, String secondPackageName) throws Exception {
        String firstClassName = "First";
        String secondClassName = "Second";
        var firstUnit = createUnit(firstPackageName, firstClassName, firstContents);
        // create second compilation unit in another project to test index selection
        var project1 = project;
        try {
            var projectPath =
                env.addProject("Project2");
            env.addGroovyJars(projectPath);
            env.addRequiredProject(projectPath, project1.getFullPath());

            project = env.getProject("Project2");
            createUnit(secondPackageName, secondClassName, secondContents);
        } finally {
            project = project1;
        }

        SearchPattern pattern = SearchPattern.createPattern(findType(firstClassName, firstUnit), IJavaSearchConstants.REFERENCES);
        return search(pattern, SearchEngine.createWorkspaceScope());
    }
}
