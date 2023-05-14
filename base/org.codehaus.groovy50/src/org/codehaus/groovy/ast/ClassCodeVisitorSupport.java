/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.codehaus.groovy.ast;

import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.DeclarationExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.stmt.AssertStatement;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.BreakStatement;
import org.codehaus.groovy.ast.stmt.CaseStatement;
import org.codehaus.groovy.ast.stmt.CatchStatement;
import org.codehaus.groovy.ast.stmt.ContinueStatement;
import org.codehaus.groovy.ast.stmt.DoWhileStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.ForStatement;
import org.codehaus.groovy.ast.stmt.IfStatement;
import org.codehaus.groovy.ast.stmt.ReturnStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.ast.stmt.SwitchStatement;
import org.codehaus.groovy.ast.stmt.SynchronizedStatement;
import org.codehaus.groovy.ast.stmt.ThrowStatement;
import org.codehaus.groovy.ast.stmt.TryCatchStatement;
import org.codehaus.groovy.ast.stmt.WhileStatement;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.syntax.PreciseSyntaxException;
import org.codehaus.groovy.transform.ErrorCollecting;

import java.util.HashSet;
import java.util.Set;

public abstract class ClassCodeVisitorSupport extends CodeVisitorSupport implements ErrorCollecting, GroovyClassVisitor {

    @Override
    public void visitClass(ClassNode node) {
        visitAnnotations(node);
        visitPackage(node.getPackage());
        visitImports(node.getModule());
        node.visitContents(this);
        visitObjectInitializerStatements(node);
    }

    public void visitAnnotations(AnnotatedNode node) {
        visitAnnotations(node.getAnnotations());
    }

    protected final void visitAnnotations(Iterable<AnnotationNode> nodes) {
        Set<AnnotationNode> aliases = null;
        for (AnnotationNode node : nodes) {
            // skip built-in properties
            if (!node.isBuiltIn()) {
                visitAnnotation(node);
                // GRECLIPSE add
                Iterable<AnnotationNode> original = node.getNodeMetaData("AnnotationCollector");
                if (original != null) {
                    if (aliases == null) aliases = new HashSet<>();
                    original.forEach(aliases::add);
                }
                // GRECLIPSE end
            }
        }
        // GRECLIPSE add
        if (aliases != null) visitAnnotations(aliases);
        // GRECLIPSE end
    }

    protected void visitAnnotation(AnnotationNode node) {
        for (Expression expr : node.getMembers().values()) {
            expr.visit(this);
        }
    }

    public void visitPackage(PackageNode node) {
        if (node != null) {
            visitAnnotations(node);
            node.visit(this);
        }
    }

    public void visitImports(ModuleNode node) {
        if (node != null) {
            for (ImportNode importNode : node.getImports()) {
                visitAnnotations(importNode);
                importNode.visit(this);
            }
            for (ImportNode importStarNode : node.getStarImports()) {
                visitAnnotations(importStarNode);
                importStarNode.visit(this);
            }
            for (ImportNode importStaticNode : node.getStaticImports().values()) {
                visitAnnotations(importStaticNode);
                importStaticNode.visit(this);
            }
            for (ImportNode importStaticStarNode : node.getStaticStarImports().values()) {
                visitAnnotations(importStaticStarNode);
                importStaticStarNode.visit(this);
            }
        }
    }

    @Override
    public void visitConstructor(ConstructorNode node) {
        visitConstructorOrMethod(node, true);
    }

    @Override
    public void visitMethod(MethodNode node) {
        visitConstructorOrMethod(node, false);
    }

    protected void visitConstructorOrMethod(MethodNode node, boolean isConstructor) {
        visitAnnotations(node);
        for (Parameter parameter : node.getParameters()) {
            visitAnnotations(parameter);
        }
        visitClassCodeContainer(node.getCode());
    }

    @Override
    public void visitField(FieldNode node) {
        visitAnnotations(node);
        Expression init = node.getInitialExpression();
        if (init != null) init.visit(this);
    }

    @Override
    public void visitProperty(PropertyNode node) {
        visitAnnotations(node);
        Expression init = node.getInitialExpression();
        if (init != null) init.visit(this);

        visitClassCodeContainer(node.getGetterBlock());
        visitClassCodeContainer(node.getSetterBlock());
    }

    protected void visitClassCodeContainer(Statement code) {
        if (code != null) code.visit(this);
    }

    protected void visitObjectInitializerStatements(ClassNode node) {
        for (Statement statement : node.getObjectInitializerStatements()) {
            statement.visit(this);
        }
    }

    @Override
    public void visitClosureExpression(ClosureExpression expression) {
        if (expression.isParameterSpecified()) {
            for (Parameter parameter : expression.getParameters()) {
                visitAnnotations(parameter);
            }
        }
        super.visitClosureExpression(expression);
    }

    @Override
    public void visitDeclarationExpression(DeclarationExpression expression) {
        visitAnnotations(expression);
        super.visitDeclarationExpression(expression);
    }

    // GRECLIPSE add
    @Override
    public void visitConstantExpression(ConstantExpression expression) {
        // check for inlined constant (see ExpressionUtils.transformInlineConstants)
        Expression original = expression.getNodeMetaData(ORIGINAL_EXPRESSION);
        if (original != null) {
            original.visit(this);
        }
    }

    /**
     * Returns the original source expression (in case of constant inlining) or
     * the input expression.
     *
     * @see org.codehaus.groovy.control.ResolveVisitor#transformInlineConstants
     * @see org.codehaus.groovy.control.ResolveVisitor#cloneConstantExpression
     */
    public static final Expression getNonInlinedExpression(Expression expression) {
        Expression original = expression.getNodeMetaData(ORIGINAL_EXPRESSION);
        return original != null ? original : expression;
    }

    public static final String ORIGINAL_EXPRESSION = "OriginalExpression";
    // GRECLIPSE end

    //--------------------------------------------------------------------------

    @Override
    public void visitAssertStatement(AssertStatement statement) {
        visitStatement(statement);
        super.visitAssertStatement(statement);
    }

    @Override
    public void visitBlockStatement(BlockStatement statement) {
        visitStatement(statement);
        super.visitBlockStatement(statement);
    }

    @Override
    public void visitBreakStatement(BreakStatement statement) {
        visitStatement(statement);
        super.visitBreakStatement(statement);
    }

    @Override
    public void visitCaseStatement(CaseStatement statement) {
        visitStatement(statement);
        super.visitCaseStatement(statement);
    }

    @Override
    public void visitCatchStatement(CatchStatement statement) {
        visitStatement(statement);
        visitAnnotations(statement.getVariable());
        super.visitCatchStatement(statement);
    }

    @Override
    public void visitContinueStatement(ContinueStatement statement) {
        visitStatement(statement);
        super.visitContinueStatement(statement);
    }

    @Override
    public void visitDoWhileLoop(DoWhileStatement statement) {
        visitStatement(statement);
        super.visitDoWhileLoop(statement);
    }

    @Override
    public void visitExpressionStatement(ExpressionStatement statement) {
        visitStatement(statement);
        super.visitExpressionStatement(statement);
    }

    @Override
    public void visitForLoop(ForStatement statement) {
        visitStatement(statement);
        visitAnnotations(statement.getVariable());
        super.visitForLoop(statement);
    }

    @Override
    public void visitIfElse(IfStatement statement) {
        visitStatement(statement);
        super.visitIfElse(statement);
    }

    @Override
    public void visitReturnStatement(ReturnStatement statement) {
        visitStatement(statement);
        super.visitReturnStatement(statement);
    }

    @Override
    public void visitSwitch(SwitchStatement statement) {
        visitStatement(statement);
        super.visitSwitch(statement);
    }

    @Override
    public void visitSynchronizedStatement(SynchronizedStatement statement) {
        visitStatement(statement);
        super.visitSynchronizedStatement(statement);
    }

    @Override
    public void visitThrowStatement(ThrowStatement statement) {
        visitStatement(statement);
        super.visitThrowStatement(statement);
    }

    @Override
    public void visitTryCatchFinally(TryCatchStatement statement) {
        visitStatement(statement);
        super.visitTryCatchFinally(statement);
    }

    @Override
    public void visitWhileLoop(WhileStatement statement) {
        visitStatement(statement);
        super.visitWhileLoop(statement);
    }

    //--------------------------------------------------------------------------

    protected void visitStatement(Statement statement) {
    }

    /* GRECLIPSE edit
    protected abstract SourceUnit getSourceUnit();
    */
    protected SourceUnit getSourceUnit() {
        return null;
    }
    // GRECLIPSE end

    @Override
    public void addError(final String error, final ASTNode node) {
        /* GRECLIPSE edit
        getSourceUnit().addErrorAndContinue(new SyntaxException(error + '\n', node));
        */
        int start, end;
        if (node instanceof AnnotatedNode && ((AnnotatedNode) node).getNameEnd() > 0) {
            start = ((AnnotatedNode) node).getNameStart();
            end = ((AnnotatedNode) node).getNameEnd();

            // check if error range should cover arguments/parameters
            Integer offset = node.getNodeMetaData("rparen.offset");
            if (offset != null) {
                end = offset;
            }
        } else if (node instanceof DeclarationExpression) {
            addError(error, ((DeclarationExpression) node).getLeftExpression());
            return;
        } else if (node instanceof AnnotationNode) {
            start = node.getStart();
            end = ((AnnotationNode) node).getClassNode().getEnd() - 1;
        } else {
            start = node.getStart();
            end = node.getEnd() - 1;
        }
        getSourceUnit().addErrorAndContinue(new PreciseSyntaxException(
            error + '\n', node.getLineNumber(), node.getColumnNumber(), start, end
        ));
        // GRECLIPSE end
    }
}
