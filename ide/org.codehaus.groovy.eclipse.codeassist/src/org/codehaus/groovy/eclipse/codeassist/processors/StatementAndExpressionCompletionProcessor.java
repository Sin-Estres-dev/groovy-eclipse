/*
 * Copyright 2009-2024 the original author or authors.
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
package org.codehaus.groovy.eclipse.codeassist.processors;

import static org.apache.groovy.ast.tools.ExpressionUtils.isThisExpression;
import static org.codehaus.groovy.ast.tools.GeneralUtils.isOrImplements;
import static org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport.isClassClassNodeWrappingConcreteType;
import static org.codehaus.groovy.transform.trait.Traits.isTrait;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import groovy.lang.Closure;

import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.ConstructorNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.ImportNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.PropertyNode;
import org.codehaus.groovy.ast.Variable;
import org.codehaus.groovy.ast.expr.BinaryExpression;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.MethodCall;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.MethodPointerExpression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.expr.StaticMethodCallExpression;
import org.codehaus.groovy.ast.expr.TupleExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.eclipse.codeassist.GroovyContentAssist;
import org.codehaus.groovy.eclipse.codeassist.ProposalUtils;
import org.codehaus.groovy.eclipse.codeassist.creators.AbstractProposalCreator;
import org.codehaus.groovy.eclipse.codeassist.creators.CategoryProposalCreator;
import org.codehaus.groovy.eclipse.codeassist.creators.FieldProposalCreator;
import org.codehaus.groovy.eclipse.codeassist.creators.IProposalCreator;
import org.codehaus.groovy.eclipse.codeassist.creators.MethodProposalCreator;
import org.codehaus.groovy.eclipse.codeassist.proposals.AbstractGroovyProposal;
import org.codehaus.groovy.eclipse.codeassist.proposals.GroovyFieldProposal;
import org.codehaus.groovy.eclipse.codeassist.proposals.IGroovyProposal;
import org.codehaus.groovy.eclipse.codeassist.requestor.ContentAssistContext;
import org.codehaus.groovy.eclipse.codeassist.requestor.ContentAssistLocation;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.core.CompletionRequestor;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IAnnotation;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.groovy.core.util.GroovyUtils;
import org.eclipse.jdt.groovy.search.AccessorSupport;
import org.eclipse.jdt.groovy.search.ITypeRequestor;
import org.eclipse.jdt.groovy.search.TypeInferencingVisitorFactory;
import org.eclipse.jdt.groovy.search.TypeInferencingVisitorWithRequestor;
import org.eclipse.jdt.groovy.search.TypeLookupResult;
import org.eclipse.jdt.groovy.search.TypeLookupResult.TypeConfidence;
import org.eclipse.jdt.groovy.search.VariableScope;
import org.eclipse.jdt.internal.codeassist.CompletionEngine;
import org.eclipse.jdt.internal.codeassist.impl.AssistOptions;
import org.eclipse.jdt.internal.core.SearchableEnvironment;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal;
import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext;
import org.eclipse.jface.text.contentassist.ICompletionProposal;

public class StatementAndExpressionCompletionProcessor extends AbstractGroovyCompletionProcessor {

    public static final Pattern FIELD_ACCESS_COMPLETION =
        Pattern.compile(".+\\.@\\s*(?:\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*)?", Pattern.DOTALL);
    public static final Pattern METHOD_POINTER_COMPLETION =
        Pattern.compile(".+(\\.&|::)\\s*(?:\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*)?", Pattern.DOTALL);

    /**
     * The ASTNode being completed.
     */
    private final ASTNode completionNode;

    /**
     * The LHS of the assignment associated with this content assist invocation, or {@code null} if there is none.
     */
    private final ASTNode lhsNode;

    public StatementAndExpressionCompletionProcessor(final ContentAssistContext context,
            final JavaContentAssistInvocationContext javaContext, final SearchableEnvironment nameEnvironment) {
        super(context, javaContext, nameEnvironment);
        this.completionNode = context.getPerceivedCompletionNode();
        this.lhsNode = context.lhsNode;
    }

    @Override
    public List<ICompletionProposal> generateProposals(final IProgressMonitor monitor) {
        ContentAssistContext context = getContext();
        TypeInferencingVisitorWithRequestor visitor =
            new TypeInferencingVisitorFactory().createVisitor(context.unit);
        ExpressionCompletionRequestor requestor = new ExpressionCompletionRequestor();
        AssistOptions options = new AssistOptions(getJavaContext().getProject().getOptions(true));

        // if completion node is null, it is likely because of a syntax error
        if (completionNode != null) {
            visitor.visitCompilationUnit(requestor);
        }

        List<IGroovyProposal> groovyProposals = new ArrayList<>(64);
        boolean isStatic, isPrimary = (context.location == ContentAssistLocation.STATEMENT ||
            context.location == ContentAssistLocation.METHOD_CONTEXT && isReceiverImplicit(context));
        ClassNode completionType;

        if (requestor.visitSuccessful) {
            isStatic = requestor.isStatic;
            context.lhsType = requestor.lhsType;
            completionType = getCompletionType(completionNode, context, requestor);

            VariableScope currentScope = requestor.currentScope;
            List<IProposalCreator> creators = chooseProposalCreators(context, completionType);
            proposalCreatorOuterLoop(groovyProposals, creators, requestor, context, options, completionType, isStatic, isPrimary);

            requestor.currentScope = currentScope;

            if (isPrimary) {
                // if receiver type is an enum, propose its constants directly
                if (context.lhsType != null && context.lhsType.isEnum() && !context.lhsType.equals(completionType)) {
                    List<IGroovyProposal> enumFields = new FieldProposalCreator().findAllProposals(
                        context.lhsType, Collections.EMPTY_SET, context.getPerceivedCompletionExpression(), true, true);
                    for (Iterator<IGroovyProposal> it = enumFields.iterator(); it.hasNext();) {
                        GroovyFieldProposal proposal = (GroovyFieldProposal) it.next();
                        if (proposal.getField().isEnum()) {
                            proposal.setRequiredStaticImport(
                                context.lhsType.getName() + '.' + proposal.getField().getName());
                        } else {
                            it.remove();
                        }
                    }
                    groovyProposals.addAll(enumFields);
                }
            }
        } else {
            isStatic = false; // why?
            completionType = (context.containingDeclaration instanceof ClassNode
                ? (ClassNode) context.containingDeclaration : context.unit.getModuleNode().getScriptClassDummy());

            // if we are at the statement location of a script,
            // return the category proposals only
            AnnotatedNode node = context.containingDeclaration;
            ClassNode containingClass;
            if (node instanceof ClassNode) {
                containingClass = (ClassNode) node;
            } else if (node instanceof MethodNode) {
                containingClass = ((MethodNode) node).getDeclaringClass();
            } else {
                containingClass = null;
            }
            if (containingClass != null) {
                Collection<IProposalCreator> creators = Collections.singleton(new CategoryProposalCreator());
                requestor.categories = new VariableScope(null, context.unit.getModuleNode(), true).getCategoryNames();
                proposalCreatorInnerLoop(groovyProposals, creators, requestor, context, options, completionType, isStatic, isPrimary);
            } else if (node instanceof ImportNode) {
                ImportNode importNode = (ImportNode) node;
                if (importNode.isStatic()) {
                    containingClass = importNode.getType();
                    groovyProposals.addAll(new FieldProposalCreator().findAllProposals(
                        containingClass, Collections.EMPTY_SET, context.getPerceivedCompletionExpression(), true, isPrimary));
                    groovyProposals.addAll(new MethodProposalCreator().findAllProposals(
                        containingClass, Collections.EMPTY_SET, context.getPerceivedCompletionExpression(), true, isPrimary));

                    groovyProposals.removeIf(proposal -> {
                        if (proposal instanceof AbstractGroovyProposal) {
                            int flags = ((AbstractGroovyProposal) proposal).getAssociatedNodeFlags();
                            return (!Flags.isStatic(flags) || Flags.isPrivate(flags) || Flags.isSynthetic(flags));
                        }
                        return false;
                    });
                }
            }
        }

        // get proposals from providers (TODO: Should this move into proposalCreatorLoop?)
        try {
            context.currentScope = (requestor.currentScope != null ? requestor.currentScope : createTopLevelScope(completionType));
            List<IProposalProvider> providers = ProposalProviderRegistry.getRegistry().getProvidersFor(context.unit);
            for (IProposalProvider provider : providers) {
                try {
                    List<IGroovyProposal> otherProposals = provider.getStatementAndExpressionProposals(context, completionType, isStatic, requestor.categories);
                    if (otherProposals != null && !otherProposals.isEmpty()) {
                        groovyProposals.addAll(otherProposals);
                    }
                } catch (Exception e) {
                    GroovyContentAssist.logError("Exception when using third party proposal provider: " + provider.getClass().getCanonicalName(), e);
                }
            }
        } catch (CoreException e) {
            GroovyContentAssist.logError("Exception accessing proposal provider registry", e);
        }

        getContext().extend(getJavaContext().getCoreContext(), requestor.currentScope);

        // extra filtering and sorting provided by third parties
        try {
            List<IProposalFilter> filters = ProposalProviderRegistry.getRegistry().getFiltersFor(context.unit);
            for (IProposalFilter filter : filters) {
                try {
                    List<IGroovyProposal> newProposals = filter.filterProposals(groovyProposals, context, getJavaContext());
                    if (newProposals != null) {
                        groovyProposals = newProposals;
                    }
                } catch (Exception e) {
                    GroovyContentAssist.logError("Exception when using third party proposal filter: " + filter.getClass().getCanonicalName(), e);
                }
            }
        } catch (CoreException e) {
            GroovyContentAssist.logError("Exception accessing proposal provider registry", e);
        }

        return createJavaProposals(groovyProposals, options.checkDeprecation, monitor);
    }

    private List<IProposalCreator> chooseProposalCreators(final ContentAssistContext context, final ClassNode completionType) {

        if (FIELD_ACCESS_COMPLETION.matcher(context.fullCompletionExpression).matches() || context.containingCodeBlock instanceof AnnotationNode) {
            return Arrays.asList(new FieldProposalCreator());
        }

        if (METHOD_POINTER_COMPLETION.matcher(context.fullCompletionExpression).matches() || isOrImplements(completionType, VariableScope.MAP_CLASS_NODE)) {
            return Arrays.asList(new MethodProposalCreator(), new CategoryProposalCreator());
        }

        return Arrays.asList(getProposalCreators());
    }

    private void proposalCreatorOuterLoop(final Collection<IGroovyProposal> groovyProposals, final Collection<IProposalCreator> creators,
            final ExpressionCompletionRequestor requestor, final ContentAssistContext context, final AssistOptions options,
            final ClassNode completionType, final boolean isStatic, final boolean isPrimary) {

        int closureStrategy = -1;
        if (isPrimary && requestor.currentScope.getEnclosingClosure() != null) {
            closureStrategy = requestor.currentScope.getEnclosingClosureResolveStrategy();
        }

        boolean isClass = isClassClassNodeWrappingConcreteType(completionType);
        // if completionType is delegate, use instance (non-static) semantics
        boolean isStatic1 = (isStatic && closureStrategy < Closure.OWNER_FIRST);
        // "Foo.bar" and "Foo.@bar" are static; "Foo.&bar" and "Foo::bar" are not static
        boolean isStatic2 = !METHOD_POINTER_COMPLETION.matcher(context.fullCompletionExpression).matches();

        if (isClass) {
            ClassNode ofType = VariableScope.getFirstGenerics(completionType);
            if (!(context.completionNode instanceof ClassExpression) || !isTrait(ofType))
                proposalCreatorInnerLoop(groovyProposals, creators, requestor, context, options, ofType, isStatic2, isPrimary);
        }
        if (!isClass || isStatic2 || (
                !VariableScope.getFirstGenerics(completionType).equals(VariableScope.CLASS_CLASS_NODE) &&
                !VariableScope.getFirstGenerics(completionType).equals(VariableScope.OBJECT_CLASS_NODE) &&
                GroovyUtils.getGroovyVersion().getMajor() >= 4)) { // GROOVY-8633, GROOVY-10057: "Foo.&bar" supports Class methods
            proposalCreatorInnerLoop(groovyProposals, creators, requestor, context, options, completionType, isStatic1, isPrimary);
        }

        // within a closure, include content assist for the enclosing type (aka "owner")
        if (closureStrategy >= Closure.OWNER_FIRST) {
            ClassNode enclosingType = requestor.currentScope.getOwner();
            if (enclosingType != null) {
                Collection<IGroovyProposal> ownerProposals = new ArrayList<>();

                if (!enclosingType.equals(completionType)) {
                    proposalCreatorInnerLoop(ownerProposals, creators, requestor, context, options, enclosingType, isStatic, isPrimary);
                }
                if ((requestor.currentScope = enclosingType.getNodeMetaData("outer.scope")) != null) {
                    proposalCreatorOuterLoop(ownerProposals, creators, requestor, context, options, requestor.currentScope.getDelegate(), isStatic, isPrimary);
                }

                // if "delegate" and/or "owner" qualifiers are required, add them now
                setClosureQualifiers(groovyProposals, ownerProposals, closureStrategy);

                groovyProposals.addAll(ownerProposals);
            }
        }
    }

    private void proposalCreatorInnerLoop(final Collection<IGroovyProposal> groovyProposals, final Collection<IProposalCreator> creators,
            final ExpressionCompletionRequestor requestor, final ContentAssistContext context, final AssistOptions options,
            final ClassNode completionType, final boolean isStatic, final boolean isPrimary) {

        for (IProposalCreator creator : creators) {
            if (creator instanceof AbstractProposalCreator) {
                ((AbstractProposalCreator) creator).setCurrentScope(requestor.currentScope);
                ((AbstractProposalCreator) creator).setFavoriteStaticMembers(context.getFavoriteStaticMembers());
                ((AbstractProposalCreator) creator).setNameMatchingStrategy((String pattern, String candidate) -> {
                    return ProposalUtils.matches(pattern, candidate, options.camelCaseMatch, options.subwordMatch);
                });
            }
            String completionString = context.getPerceivedCompletionExpression();
            groovyProposals.addAll(
                creator.findAllProposals(completionType, requestor.categories, completionString, isStatic, isPrimary));
        }
    }

    private List<ICompletionProposal> createJavaProposals(final Collection<IGroovyProposal> groovyProposals, final boolean checkDeprecation, final IProgressMonitor monitor) {
        ContentAssistContext context = getContext();
        JavaContentAssistInvocationContext javaContext = getJavaContext();
        //@formatter:off
        CompletionRequestor completionRequestor = new CompletionRequestor() { @Override public void accept(final org.eclipse.jdt.core.CompletionProposal proposal) {} };
        CompletionEngine engine = new CompletionEngine(getNameEnvironment(), completionRequestor, javaContext.getProject().getOptions(true), javaContext.getProject(), null, monitor);
        //@formatter:on

        Map<String, List<IJavaCompletionProposal>> javaProposals = new HashMap<>(groovyProposals.size() * 3 / 2);
        for (IGroovyProposal groovyProposal : groovyProposals) {
            try {
                IJavaCompletionProposal javaProposal = groovyProposal.createJavaProposal(engine, context, javaContext);
                if (javaProposal != null) {
                    if (checkDeprecation) {
                        CompletionProposal proposal = extractProposal(javaProposal);
                        if (proposal != null && Flags.isDeprecated(proposal.getFlags())) {
                            continue;
                        }
                    }

                    String signature = getProposalSignature(javaProposal.getDisplayString());
                  //javaProposals.computeIfAbsent(signature, k -> new ArrayList<>()).add(javaProposal);
                    javaProposals.merge(signature, Collections.singletonList(javaProposal), (list, one) -> {
                        if (list.size() == 1) list = new ArrayList<>(list);
                        list.add(one.get(0));
                        return list;
                    });
                }
            } catch (Exception e) {
                GroovyContentAssist.logError("Exception when creating groovy completion proposal", e);
            }
        }

        List<ICompletionProposal> completionProposals = new ArrayList<>(javaProposals.size());
        for (List<IJavaCompletionProposal> group : javaProposals.values()) {
            int n = group.size();
            if (n == 1) {
                completionProposals.add(group.get(0));
            } else { // de-duplicate the proposal group
                Map<String, IJavaCompletionProposal> map = new HashMap<>();
                for (IJavaCompletionProposal jcp : group) {
                    String key = jcp.getDisplayString();
                    int i = key.indexOf(" - ") + 3;
                    int j = key.indexOf(' ', i);
                    if (j < 0) j = key.length();
                    key = key.substring(i, j);

                    map.merge(key, jcp, (one, two) -> {
                        // TODO: break ties between unqualified and fully-qualified declaring types
                        return (one.getRelevance() > two.getRelevance() ? one : two);
                    });
                }
                if (map.size() > 1) {
                    map.remove("DefaultGroovyMethods");
                }
                completionProposals.addAll(map.values());
            }
        }
        return completionProposals;
    }

    private static void setClosureQualifiers(final Collection<IGroovyProposal> delegateProposals, final Collection<IGroovyProposal> ownerProposals, final int resolveStrategy) {

        Function<IGroovyProposal, String> toName = (p) -> {
            AnnotatedNode node = ((AbstractGroovyProposal) p).getAssociatedNode();
            if (node instanceof FieldNode) {
                return ((FieldNode) node).getName();
            }
            if (node instanceof MethodNode) {
                return ((MethodNode) node).getName();
            }
            if (node instanceof PropertyNode) {
                return ((PropertyNode) node).getName();
            }
            throw new IllegalStateException("unexpected node type: " + node.getClass());
        };

        Predicate<IGroovyProposal> isQualified = (p) -> {
            return ((AbstractGroovyProposal) p).getRequiredQualifier() != null;
        };

        BiConsumer<IGroovyProposal, String> addQualifier = (p, q) -> {
            if (isQualified.test(p)) {
                q += "." + ((AbstractGroovyProposal) p).getRequiredQualifier();
            }
            ((AbstractGroovyProposal) p).setRequiredQualifier(q);
        };

        Consumer<IGroovyProposal> reduceRelevance = (p) -> {
            AbstractGroovyProposal agp = (AbstractGroovyProposal) p;
            agp.setRelevanceMultiplier(agp.getRelevanceMultiplier() * 0.999f);
        };

        //

        if (!delegateProposals.isEmpty()) {
            Consumer<IGroovyProposal> addDelegateQualifier = bind(addQualifier, "delegate");

            if (resolveStrategy == Closure.OWNER_FIRST && !ownerProposals.isEmpty()) {
                Set<String> names = ownerProposals.stream().map(toName).collect(Collectors.toSet());
                delegateProposals.stream().filter(p -> names.contains(toName.apply(p)))
                                .forEach(addDelegateQualifier.andThen(reduceRelevance));
            } else if (resolveStrategy == Closure.TO_SELF) {
                delegateProposals.forEach(addDelegateQualifier.andThen(reduceRelevance));
            }
        }

        if (!ownerProposals.isEmpty()) {
            Consumer<IGroovyProposal> addOwnerQualifier = bind(addQualifier, "owner");

            if (resolveStrategy == Closure.DELEGATE_FIRST && !delegateProposals.isEmpty()) {
                Set<String> names = delegateProposals.stream().map(toName).collect(Collectors.toSet());
                ownerProposals.stream().filter(isQualified.or(p -> names.contains(toName.apply(p))))
                                                .forEach(addOwnerQualifier.andThen(reduceRelevance));
            } else if (resolveStrategy == Closure.TO_SELF) {
                ownerProposals.forEach(addOwnerQualifier.andThen(reduceRelevance));
            } else {
                ownerProposals.stream().filter(isQualified).forEach(addOwnerQualifier);
            }
        }
    }

    private static ClassNode getCompletionType(final ASTNode completionNode, final ContentAssistContext context, final ExpressionCompletionRequestor requestor) {
        ClassNode completionType;

        switch (context.location) {
        case EXPRESSION:
            completionType = requestor.resultingType;
            break;
        case METHOD_CONTEXT:
            // completing on a variable expression here, that means we have something like: myMethodCall _
            // so, we want to look at the type of 'this' to complete on
            completionType = (completionNode instanceof VariableExpression
                ? requestor.currentScope.getDelegateOrThis() : requestor.resultingType);
            break;
        default:
            // use the current 'this' type so that closure types are correct
            completionType = requestor.currentScope.getDelegateOrThis();
            if (completionType == null) {
                // will only happen if in top level scope
                completionType = requestor.resultingType;
            }
        }

        if (completionType == null) {
            if (context.containingDeclaration instanceof ClassNode) {
                completionType = (ClassNode) context.containingDeclaration;
            } else {
                completionType = context.unit.getModuleNode().getScriptClassDummy();
            }
        }

        return completionType;
    }

    private static String getProposalSignature(final String completionProposalDescription) {
        String sig = completionProposalDescription;
        int idx = sig.indexOf(" : "); // type, etc.
        if (idx != -1) sig = sig.substring(0, idx);

        if (sig.endsWith(")") && !sig.endsWith("()")) {
            // remove parameter generics and names
            String[] tokens = sig.split("[()]|, ");

            StringBuilder sb = new StringBuilder(tokens[0]).append('(');
            for (int i = 1, n = tokens.length; i < n; i += 1) {
                int j = tokens[i].indexOf('<');
                if (j < 0) {
                    j = tokens[i].lastIndexOf(' ');
                    if (j < 0) j = tokens[i].length();
                    sb.append(tokens[i], 0, j); // type name
                } else {
                    sb.append(tokens[i], 0, j); // type name

                    j = tokens[i].lastIndexOf('>');
                    int k = tokens[i].indexOf(' ', j);
                    if (k < 0) k = tokens[i].length();
                    sb.append(tokens[i], j + 1, k); // "[]" or "..."
                }
                sb.append(',');
            }
            sb.setCharAt(sb.length() - 1, ')');
            sig = sb.toString();
        }
        return sig;
    }

    private static boolean isReceiverImplicit(final ContentAssistContext methodContext) {
        if (methodContext.completionNode instanceof MethodCallExpression) {
            MethodCallExpression methodCall = (MethodCallExpression) methodContext.completionNode;
            return methodCall.isImplicitThis() && isThisExpression(methodCall.getObjectExpression());
        }
        if (methodContext.completionNode instanceof VariableExpression || // "name |"
                methodContext.completionNode instanceof StaticMethodCallExpression) {
            return true;
        }
        return false;
    }

    private static VariableScope createTopLevelScope(final ClassNode completionType) {
        return new VariableScope(null, completionType, false);
    }

    private static <A, B> Consumer<A> bind(final BiConsumer<A, B> consumer, final B b) {
        return (A a) -> consumer.accept(a, b);
    }

    //--------------------------------------------------------------------------

    private class ExpressionCompletionRequestor implements ITypeRequestor {

        /** number of array accesses that must be dereferenced */
        private int derefCount;
        private boolean isStatic;
        private ClassNode lhsType;
        private ClassNode resultingType;
        private Set<ClassNode> categories;
        private Expression arrayAccessLHS;
        private VariableScope currentScope;

        private boolean visitSuccessful;

        private ExpressionCompletionRequestor() {
            // remember the rightmost part of the LHS of a binary expression
            ASTNode maybeLHS = getContext().getPerceivedCompletionNode();
            while (maybeLHS != null) {
                if (maybeLHS instanceof BinaryExpression) {
                    arrayAccessLHS = ((BinaryExpression) maybeLHS).getLeftExpression();
                    maybeLHS = arrayAccessLHS;
                    derefCount += 1;
                } else if (maybeLHS instanceof PropertyExpression) {
                    arrayAccessLHS = ((PropertyExpression) maybeLHS).getObjectExpression();
                    maybeLHS = ((PropertyExpression) maybeLHS).getProperty();
                } else if (maybeLHS instanceof MethodCallExpression) {
                    arrayAccessLHS = ((MethodCallExpression) maybeLHS).getObjectExpression();
                    maybeLHS = ((MethodCallExpression) maybeLHS).getMethod();
                } else {
                    if (maybeLHS instanceof Expression) {
                        arrayAccessLHS = (Expression) maybeLHS;
                    }
                    maybeLHS = null;
                }
            }
        }

        @Override
        public VisitStatus acceptASTNode(final ASTNode node, final TypeLookupResult result, final IJavaElement enclosingElement) {
            // check to see if the enclosing element does not enclose the nodeToLookFor
            if (!isInterestingElement(enclosingElement)) {
                return VisitStatus.CANCEL_MEMBER;
            }

            if (node instanceof ClassNode) {
                if (GroovyUtils.isScript((ClassNode) node)) {
                    return VisitStatus.CONTINUE;
                }
            } else if (node instanceof MethodNode && !(node instanceof ConstructorNode)) {
                if (((MethodNode) node).isScriptBody()) {
                    return VisitStatus.CONTINUE;
                }
            } else if (node == lhsNode) {
                // NOTE: this should be mutually exclusive to maybeRememberTypeOfLHS()
                // save the inferred type of the LHS node for ranking of the proposals
                if (result.confidence != TypeConfidence.UNKNOWN) {
                    if (result.declaration instanceof MethodNode) {
                        MethodNode meth = (MethodNode) result.declaration;
                        if (AccessorSupport.SETTER.isAccessorKind(meth, false)) {
                            lhsType = meth.getParameters()[0].getType();
                        }
                    } else {
                        lhsType = result.type;
                    }
                    if (VariableScope.OBJECT_CLASS_NODE.equals(lhsType)) {
                        lhsType = null;
                    }
                }
            }

            boolean derefList = false; // if true use the parameterized type of the list
            boolean success = doTest(node);
            if (!success) {
                // maybe this is content assist after array access, i.e. foo[0]._
                derefList = doTestForAfterArrayAccess(node);
                success = derefList;
            }
            if (success) {
                visitSuccessful = true;
                currentScope = result.scope;
                maybeRememberTypeOfLHS(result);
                setResultingType(result, derefList);
                categories = result.scope.getCategoryNames();
                isStatic = (getContext().containingCodeBlock instanceof AnnotationNode ||
                    // if we are completing on '.class' then never static context
                    (node instanceof ClassExpression && !VariableScope.CLASS_CLASS_NODE.equals(resultingType)) ||
                    (node instanceof StaticMethodCallExpression && getContext().location != ContentAssistLocation.EXPRESSION));
                return VisitStatus.STOP_VISIT;
            }
            return VisitStatus.CONTINUE;
        }

        private void setResultingType(final TypeLookupResult result, final boolean derefList) {
            ContentAssistContext context = getContext();

            if (context.location == ContentAssistLocation.METHOD_CONTEXT) { // for method or constructor
                resultingType = result.receiverType != null ? result.receiverType : result.declaringType;
            } else if (result.declaration == null && result.declaringType != null) {
                resultingType = result.declaringType;
            } else {
                resultingType = result.type;
            }

            if (derefList) {
                for (int i = 0; i < derefCount; i += 1) {
                    // GRECLIPSE-742: does the LHS type have a 'getAt' method?
                    boolean getAtFound = false;
                    List<MethodNode> getAts = resultingType.getMethods("getAt");
                    for (MethodNode getAt : getAts) {
                        if (getAt.getParameters() != null && getAt.getParameters().length == 1) {
                            resultingType = getAt.getReturnType();
                            getAtFound = true;
                        }
                    }
                    if (!getAtFound) {
                        if (VariableScope.MAP_CLASS_NODE.equals(resultingType)) {
                            // for maps, always use the type of value
                            resultingType = resultingType.getGenericsTypes()[1].getType();
                        } else {
                            for (int j = 0; j < derefCount; j += 1) {
                                resultingType = VariableScope.extractElementType(resultingType);
                            }
                        }
                    }
                }
            }

            ASTNode enclosing = result.scope.getEnclosingNode();
            if (enclosing instanceof PropertyExpression) {
                // if enclosing is method pointer expression, result type is Closure<T>, not just T
                if (((PropertyExpression) enclosing).getObjectExpression() instanceof MethodPointerExpression) {
                    resultingType = result.declaringType;
                } else if (((PropertyExpression) enclosing).isSpreadSafe()) {
                    resultingType = VariableScope.extractSpreadType(resultingType);
                }
            } else if (enclosing instanceof MethodCallExpression) {
                if (((MethodCallExpression) enclosing).isSpreadSafe()) {
                    resultingType = VariableScope.extractSpreadType(resultingType);
                }
            }
            resultingType = GroovyUtils.getWrapperTypeIfPrimitive(resultingType);
        }

        /**
         * Determines if this is the lhs of an array access -- the 'foo' of 'foo[0]'.
         */
        private boolean doTestForAfterArrayAccess(final ASTNode node) {
            return (node == arrayAccessLHS);
        }

        private void maybeRememberTypeOfLHS(final TypeLookupResult result) {
            if (isAssignmentOfLHS(result.enclosingAssignment)) {
                // check to see if this is the rhs of an assignment.
                // if so, then attempt to use the type of the lhs for
                // ordering of the proposals
                if (lhsNode instanceof Variable) {
                    Variable variable = (Variable) lhsNode;
                    lhsType = Optional.ofNullable(result.scope.lookupName(variable.getName())).map(info -> info.type).orElse(variable.getType());
                } else if (lhsNode instanceof PropertyExpression) {
                    lhsType = ((PropertyExpression) lhsNode).getProperty().getType();
                }
            } else {
                VariableScope.CallAndType cat = result.scope.getEnclosingMethodCallExpression();
                if (cat != null && cat.declaration instanceof MethodNode) {
                    Parameter[] params = ((MethodNode) cat.declaration).getParameters();
                    // is a method parameter the receiver of the completion expression?
                    int paramIndex = getParameterPosition(result.declaration, cat.call);
                    if (paramIndex != -1 && params.length > 0) {
                        lhsType = params[Math.min(paramIndex, params.length - 1)].getType();
                        if (lhsType.isArray() && paramIndex >= params.length - 1) {
                            lhsType = lhsType.getComponentType();
                        }
                    }
                }

                ASTNode enclosingBlock = getContext().containingCodeBlock;
                if (enclosingBlock instanceof AnnotationNode && lhsNode instanceof Variable) {
                    ClassNode annotation = ((AnnotationNode) enclosingBlock).getClassNode();
                    MethodNode attribute = annotation.getMethod(((Variable) lhsNode).getName(), Parameter.EMPTY_ARRAY);
                    lhsType = attribute.getReturnType();
                }
            }
            if (VariableScope.OBJECT_CLASS_NODE.equals(lhsType)) {
                lhsType = null;
            }
        }

        private boolean isAssignmentOfLHS(final BinaryExpression node) {
            if (node != null && lhsNode != null) {
                Expression expression = node.getLeftExpression();
                return expression == lhsNode || (expression.getClass() == lhsNode.getClass() &&
                    expression.getStart() == lhsNode.getStart() && expression.getEnd() == lhsNode.getEnd());
            }
            return false;
        }

        private int getParameterPosition(final ASTNode argumentCandidate, final MethodCall methodCall) {
            if (methodCall != null && methodCall.getArguments() instanceof TupleExpression) {
                int paramIndex = -1;
                for (Expression argument : (TupleExpression) methodCall.getArguments()) {
                    paramIndex += 1;
                    if (argument == argumentCandidate) {
                        return paramIndex;
                    }
                }
            }
            return -1;
        }

        private boolean doTest(final ASTNode node) {
            if (node instanceof PropertyExpression || node instanceof TupleExpression) {
                // never complete on a property expression, but rather on its getProperty() result
                // never complete on a list of expressions, but rather on its getExpressions() result
                return false;
            } else if (node instanceof BinaryExpression &&
                    ((BinaryExpression) node).getLeftExpression() == arrayAccessLHS) {
                // wait for LHS to come through so the dereferenced value can be used as the completion type
                return false;
            }

            boolean rangeMatch = false;
            if (completionNode != null) {
                rangeMatch = (completionNode.getStart() == node.getStart() && completionNode.getEnd() == node.getEnd());
            }
            return (rangeMatch && isNotExpressionAndStatement(completionNode, node));
        }

        /**
         * @return {@code true} if {@code enclosingElement} contains {@link #completionNode} or is necessary for type inference
         *
         * @see org.codehaus.groovy.eclipse.codebrowsing.requestor.CodeSelectRequestor#interestingElement(IJavaElement)
         */
        private boolean isInterestingElement(final IJavaElement enclosingElement) {
            try {
                switch (enclosingElement.getElementType()) {
                case IJavaElement.FIELD:
                    if ("Qjava.lang.Object;".equals(((IField) enclosingElement).getTypeSignature())) {
                        return true;
                    }
                    break;
                case IJavaElement.METHOD:
                    if (isInitializerMethod((IMethod) enclosingElement)) {
                        return true;
                    }
                    break;
                case IJavaElement.INITIALIZER:
                    return true;
                }

                if (enclosingElement instanceof ISourceReference) {
                    ISourceRange range = ((ISourceReference) enclosingElement).getSourceRange();
                    if (range.getOffset() <= completionNode.getStart() && range.getOffset() + range.getLength() >= completionNode.getEnd()) {
                        return true;
                    }
                }
            } catch (JavaModelException e) {
                GroovyContentAssist.logError(e);
            }
            return false;
        }

        private boolean isInitializerMethod(final IMethod method) throws JavaModelException {
            if (method.isConstructor()) {
                return true;
            }
            if (!Flags.isStatic(method.getFlags())) {
                for (IAnnotation annotation : method.getAnnotations()) {
                    String name = annotation.getElementName();
                    if (name.endsWith("PostConstruct")) {
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean isNotExpressionAndStatement(final ASTNode thisNode, final ASTNode thatNode) {
            if (thisNode instanceof Expression) {
                return !(thatNode instanceof Statement);
            } else if (thisNode instanceof Statement) {
                return !(thatNode instanceof Expression);
            }
            return true;
        }
    }
}
