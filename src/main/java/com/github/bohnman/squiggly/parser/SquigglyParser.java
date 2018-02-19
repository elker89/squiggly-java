package com.github.bohnman.squiggly.parser;

import com.github.bohnman.squiggly.config.SquigglyConfig;
import com.github.bohnman.squiggly.metric.SquigglyMetrics;
import com.github.bohnman.squiggly.metric.source.GuavaCacheSquigglyMetricsSource;
import com.github.bohnman.squiggly.name.AnyDeepName;
import com.github.bohnman.squiggly.name.AnyShallowName;
import com.github.bohnman.squiggly.name.ExactName;
import com.github.bohnman.squiggly.name.RegexName;
import com.github.bohnman.squiggly.name.SquigglyName;
import com.github.bohnman.squiggly.name.VariableName;
import com.github.bohnman.squiggly.name.WildcardName;
import com.github.bohnman.squiggly.parser.antlr4.SquigglyExpressionBaseVisitor;
import com.github.bohnman.squiggly.parser.antlr4.SquigglyExpressionLexer;
import com.github.bohnman.squiggly.parser.antlr4.SquigglyExpressionParser;
import com.github.bohnman.squiggly.util.antlr4.ThrowingErrorListener;
import com.github.bohnman.squiggly.view.PropertyView;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.jcip.annotations.ThreadSafe;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The parser takes a filter expression and compiles it to an Abstract Syntax Tree (AST).  In this parser's case, the
 * tree doesn't have a root node but rather just returns top level nodes.
 */
@ThreadSafe
public class SquigglyParser {

    // Caches parsed filter expressions
    private final Cache<String, List<SquigglyNode>> cache;

    public SquigglyParser(SquigglyConfig config, SquigglyMetrics metrics) {
        cache = CacheBuilder.from(config.getParserNodeCacheSpec()).build();
        metrics.add(new GuavaCacheSquigglyMetricsSource("squiggly.parser.nodeCache.", cache));
    }

    /**
     * Parse a filter expression.
     *
     * @param filter the filter expression
     * @return compiled nodes
     */
    public List<SquigglyNode> parse(String filter) {
        filter = StringUtils.trim(filter);

        if (StringUtils.isEmpty(filter)) {
            return Collections.emptyList();
        }

        // get it from the cache if we can
        List<SquigglyNode> cachedNodes = cache.getIfPresent(filter);

        if (cachedNodes != null) {
            return cachedNodes;
        }


        SquigglyExpressionLexer lexer = ThrowingErrorListener.overwrite(new SquigglyExpressionLexer(new ANTLRInputStream(filter)));
        SquigglyExpressionParser parser = ThrowingErrorListener.overwrite(new SquigglyExpressionParser(new CommonTokenStream(lexer)));

        Visitor visitor = new Visitor();
        List<SquigglyNode> nodes = Collections.unmodifiableList(visitor.visit(parser.parse()));

        cache.put(filter, nodes);
        return nodes;
    }

    private class Visitor extends SquigglyExpressionBaseVisitor<List<SquigglyNode>> {
        @Override
        public List<SquigglyNode> visitParse(SquigglyExpressionParser.ParseContext ctx) {
            MutableNode root = new MutableNode(new ExactName("root")).dotPathed(true);
            handleExpressionList(ctx.expressionList(), root);
            MutableNode analyzedRoot = analyze(root);
            return analyzedRoot.toSquigglyNode().getChildren();
        }

        private void handleExpressionList(SquigglyExpressionParser.ExpressionListContext ctx, MutableNode parent) {
            List<SquigglyExpressionParser.ExpressionContext> expressions = ctx.expression();

            for (SquigglyExpressionParser.ExpressionContext expressionContext : expressions) {
                handleExpression(expressionContext, parent);
            }
        }

        private void handleExpression(SquigglyExpressionParser.ExpressionContext ctx, MutableNode parent) {

            if (ctx.negatedExpression() != null) {
                handleNegatedExpression(ctx.negatedExpression(), parent);
            }

            List<SquigglyName> names;

            if (ctx.field() != null) {
                names = Collections.singletonList(createName(ctx.field()));
            } else if (ctx.dottedField() != null) {
                parent.squiggly = true;
                for (int i = 0; i < ctx.dottedField().field().size() - 1; i++) {
                    parent = parent.addChild(new MutableNode(createName(ctx.dottedField().field(i))).dotPathed(true));
                    parent.squiggly = true;
                }
                names = Collections.singletonList(createName(ctx.dottedField().field().get(ctx.dottedField().field().size() - 1)));
            } else if (ctx.fieldList() != null) {
                names = new ArrayList<>(ctx.fieldList().field().size());
                for (SquigglyExpressionParser.FieldContext fieldContext : ctx.fieldList().field()) {
                    names.add(createName(fieldContext));
                }
            } else if (ctx.wildcardDeepField() != null) {
                names = Collections.singletonList(AnyDeepName.get());
            } else {
                names = Collections.emptyList();
            }


            for (SquigglyName name : names) {
                MutableNode node = parent.addChild(new MutableNode(name));

                if (ctx.emptyNestedExpression() != null) {
                    node.emptyNested = true;
                } else if (ctx.nestedExpression() != null) {
                    node.squiggly = true;
                    handleExpressionList(ctx.nestedExpression().expressionList(), node);
                }
            }
        }

        private SquigglyName createName(SquigglyExpressionParser.FieldContext ctx) {
            SquigglyName name;

            if (ctx.StringLiteral() != null) {
                name = new ExactName(ctx.StringLiteral().getText());
            } else if (ctx.IntegerLiteral() != null) {
                name = new ExactName(ctx.IntegerLiteral().getText());
            } else if (ctx.Identifier() != null) {
                name = new ExactName(ctx.Identifier().getText());
            } else if (ctx.wildcardField() != null) {
                name = new WildcardName(ctx.wildcardField().getText());
            } else if (ctx.RegexLiteral() != null) {
                String fullPattern = ctx.RegexLiteral().getText();
                String pattern = fullPattern.substring(1);
                int slashIdx = pattern.indexOf('/');

                if (slashIdx < 0) {
                    slashIdx = pattern.indexOf('~');
                }

                Set<String> flags = new HashSet<>();

                if (slashIdx >= 0) {
                    String flagPart = StringUtils.trim(StringUtils.substring(pattern, slashIdx + 1));
                    pattern = StringUtils.substring(pattern, 0, slashIdx);

                    if (StringUtils.isNotEmpty(flagPart)) {
                        for (char flag : flagPart.toCharArray()) {
                            flags.add(Character.toString(flag));
                        }
                    }
                }

                name = new RegexName(pattern, flags);
            } else if (ctx.WildcardLiteral() != null) {
                if ("*".equals(ctx.WildcardLiteral().getText())) {
                    name = AnyShallowName.get();
                } else {
                    name = new WildcardName(ctx.WildcardLiteral().getText());
                }
            } else if (ctx.Variable() != null) {
                name = new VariableName(ctx.Variable().getText().substring(1));
            } else {
                throw new IllegalArgumentException("Unhandled field: " + ctx.getText());
            }

            return name;
        }


        private void handleNegatedExpression(SquigglyExpressionParser.NegatedExpressionContext ctx, MutableNode parent) {
            if (ctx.field() != null) {
                parent.addChild(new MutableNode(createName(ctx.field())).negated(true));
            } else if (ctx.dottedField() != null) {
                for (SquigglyExpressionParser.FieldContext fieldContext : ctx.dottedField().field()) {
                    parent.squiggly = true;
                    parent = parent.addChild(new MutableNode(createName(fieldContext)).dotPathed(true));
                }

                parent.negated(true);
            }
        }

    }

    private MutableNode analyze(MutableNode node) {
        if (node.children != null && !node.children.isEmpty()) {
            boolean allNegated = true;

            for (MutableNode child : node.children.values()) {
                if (!child.negated) {
                    allNegated = false;
                    break;
                }
            }

            if (allNegated) {
                node.addChild(new MutableNode(newBaseViewName()).dotPathed(node.dotPathed));
                MutableNode parent = node.parent;

                while (parent != null) {
                    parent.addChild(new MutableNode(newBaseViewName()).dotPathed(parent.dotPathed));

                    if (!parent.dotPathed) {
                        break;
                    }

                    parent = parent.parent;
                }
            } else {
                for (MutableNode child : node.children.values()) {
                    analyze(child);
                }
            }

        }

        return node;
    }

    private class MutableNode {
        private SquigglyName name;
        private boolean negated;
        private boolean squiggly;
        private boolean emptyNested;
        @Nullable
        private Map<String, MutableNode> children;
        private boolean dotPathed;
        @Nullable
        private MutableNode parent;

        MutableNode(SquigglyName name) {
            this.name = name;
        }

        SquigglyNode toSquigglyNode() {
            if (name == null) {
                throw new IllegalArgumentException("No Names specified");
            }

            List<SquigglyNode> childNodes;

            if (children == null || children.isEmpty()) {
                childNodes = Collections.emptyList();
            } else {
                childNodes = new ArrayList<>(children.size());

                for (MutableNode child : children.values()) {
                    childNodes.add(child.toSquigglyNode());
                }

            }

            return newSquigglyNode(name, childNodes);
        }

        private SquigglyNode newSquigglyNode(SquigglyName name, List<SquigglyNode> childNodes) {
            return new SquigglyNode(name, childNodes, negated, squiggly, emptyNested);
        }

        public MutableNode dotPathed(boolean dotPathed) {
            this.dotPathed = dotPathed;
            return this;
        }

        public MutableNode negated(boolean negated) {
            this.negated = negated;
            return this;
        }

        public MutableNode addChild(MutableNode childToAdd) {
            if (children == null) {
                children = new LinkedHashMap<>();
            }

            String name = childToAdd.name.getName();
            MutableNode existingChild = children.get(name);

            if (existingChild == null) {
                childToAdd.parent = this;
                children.put(name, childToAdd);
            } else {
                if (childToAdd.children != null) {

                    if (existingChild.children == null) {
                        existingChild.children = childToAdd.children;
                    } else {
                        existingChild.children.putAll(childToAdd.children);
                    }
                }


                existingChild.squiggly = existingChild.squiggly || childToAdd.squiggly;
                existingChild.emptyNested = existingChild.emptyNested && childToAdd.emptyNested;
                existingChild.dotPathed = existingChild.dotPathed && childToAdd.dotPathed;
                childToAdd = existingChild;
            }

            if (!childToAdd.dotPathed && dotPathed) {
                dotPathed = false;
            }

            return childToAdd;
        }
    }

    private ExactName newBaseViewName() {
        return new ExactName(PropertyView.BASE_VIEW);
    }


}
