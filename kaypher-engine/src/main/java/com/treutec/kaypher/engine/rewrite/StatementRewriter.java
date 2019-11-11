/*
 * Copyright 2019 Treu Techologies
 *
 * See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.treutec.kaypher.engine.rewrite;

import com.treutec.kaypher.execution.expression.tree.Expression;
import com.treutec.kaypher.execution.expression.tree.Type;
import com.treutec.kaypher.parser.tree.AliasedRelation;
import com.treutec.kaypher.parser.tree.AllColumns;
import com.treutec.kaypher.parser.tree.AstNode;
import com.treutec.kaypher.parser.tree.AstVisitor;
import com.treutec.kaypher.parser.tree.CreateStream;
import com.treutec.kaypher.parser.tree.CreateStreamAsSelect;
import com.treutec.kaypher.parser.tree.CreateTable;
import com.treutec.kaypher.parser.tree.CreateTableAsSelect;
import com.treutec.kaypher.parser.tree.DropTable;
import com.treutec.kaypher.parser.tree.Explain;
import com.treutec.kaypher.parser.tree.GroupBy;
import com.treutec.kaypher.parser.tree.GroupingElement;
import com.treutec.kaypher.parser.tree.InsertInto;
import com.treutec.kaypher.parser.tree.Join;
import com.treutec.kaypher.parser.tree.JoinCriteria;
import com.treutec.kaypher.parser.tree.JoinOn;
import com.treutec.kaypher.parser.tree.Query;
import com.treutec.kaypher.parser.tree.RegisterType;
import com.treutec.kaypher.parser.tree.Relation;
import com.treutec.kaypher.parser.tree.Select;
import com.treutec.kaypher.parser.tree.SelectItem;
import com.treutec.kaypher.parser.tree.SimpleGroupBy;
import com.treutec.kaypher.parser.tree.SingleColumn;
import com.treutec.kaypher.parser.tree.Statement;
import com.treutec.kaypher.parser.tree.Statements;
import com.treutec.kaypher.parser.tree.Table;
import com.treutec.kaypher.parser.tree.TableElement;
import com.treutec.kaypher.parser.tree.TableElements;
import com.treutec.kaypher.parser.tree.WindowExpression;
import com.treutec.kaypher.parser.tree.WithinExpression;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * This class creates a copy of the given AST, but with all AST nodes rewritten as specified by
 * a plugin, and all expressions rewritten by the provided expression rewriter.
 * <p></p>
 * A plugin is just a class that implements
 * BiFunction&lt;AstNode, Context&lt;C&gt;, Optional&lt;AstNode&gt;&gt;, and optionally returns
 * a rewritten AST node. If the empty value is returned, the rewriter rebuilds the node by
 * rewriting all its children.
 * <p></p>
 * An expression rewriter is simply a class that implements
 * BiFunction&lt;Expression, C, Expression%gt;, and returns a rewritten expression given an
 * expression from the AST.
 */
public final class StatementRewriter<C> {

  private final Rewriter<C> rewriter;

  public static final class Context<C> {
    private final C context;
    private final Rewriter<C> rewriter;

    private Context(final C context, final Rewriter<C> rewriter) {
      this.context = context;
      this.rewriter = Objects.requireNonNull(rewriter, "rewriter");
    }

    public C getContext() {
      return context;
    }

    public AstNode process(final AstNode node) {
      return rewriter.process(node, context);
    }

    public Expression process(final Expression expression) {
      return rewriter.processExpression(expression, context);
    }
  }

  /**
   * Creates a new StatementRewriter that rewrites all expressions in a statement by
   * using the provided expression-rewriter.
   * @param expressionRewriter The expression rewriter used to rewrite an expression.
   */
  StatementRewriter(
      final BiFunction<Expression, C, Expression> expressionRewriter,
      final BiFunction<AstNode, Context<C>, Optional<AstNode>> plugin) {
    this.rewriter = new Rewriter<>(expressionRewriter, plugin);
  }

  // Exposed for testing
  StatementRewriter(
      final BiFunction<Expression, C, Expression> expressionRewriter,
      final BiFunction<AstNode, Context<C>, Optional<AstNode>> plugin,
      final BiFunction<AstNode, C, AstNode> rewriter) {
    this.rewriter = new Rewriter<>(expressionRewriter, plugin, rewriter);
  }

  public AstNode rewrite(final AstNode node, final C context) {
    return rewriter.process(node, context);
  }

  // CHECKSTYLE_RULES.OFF: ClassDataAbstractionCoupling
  private static final class Rewriter<C> extends AstVisitor<AstNode, C> {
    // CHECKSTYLE_RULES.ON: ClassDataAbstractionCoupling
    private final BiFunction<Expression, C, Expression> expressionRewriter;
    private final BiFunction<AstNode, Context<C>, Optional<AstNode>> plugin;
    private final BiFunction<AstNode, C, AstNode> rewriter;

    private Rewriter(
        final BiFunction<Expression, C, Expression> expressionRewriter,
        final BiFunction<AstNode, Context<C>, Optional<AstNode>> plugin) {
      this.expressionRewriter
          = Objects.requireNonNull(expressionRewriter, "expressionRewriter");;
      this.plugin = Objects.requireNonNull(plugin, "plugin");
      this.rewriter = this::process;
    }

    private Rewriter(
        final BiFunction<Expression, C, Expression> expressionRewriter,
        final BiFunction<AstNode, Context<C>, Optional<AstNode>> plugin,
        final BiFunction<AstNode, C, AstNode> rewriter) {
      this.expressionRewriter
          = Objects.requireNonNull(expressionRewriter, "expressionRewriter");;
      this.plugin = Objects.requireNonNull(plugin, "plugin");
      this.rewriter = Objects.requireNonNull(rewriter, "rewriter");
    }

    private Expression processExpression(final Expression node, final C context) {
      return expressionRewriter.apply(node, context);
    }

    @Override
    protected AstNode visitNode(final AstNode node, final C context) {
      return node;
    }

    @Override
    protected AstNode visitStatements(final Statements node, final C context) {
      final Optional<AstNode> result = plugin.apply(node, new Context<>(context, this));
      if (result.isPresent()) {
        return result.get();
      }

      final List<Statement> rewrittenStatements = node.getStatements()
          .stream()
          .map(s -> (Statement) rewriter.apply(s, context))
          .collect(Collectors.toList());

      return new Statements(
          node.getLocation(),
          rewrittenStatements
      );
    }

    @Override
    protected AstNode visitQuery(final Query node, final C context) {
      final Optional<AstNode> result = plugin.apply(node, new Context<>(context, this));
      if (result.isPresent()) {
        return result.get();
      }

      final Select select = (Select) rewriter.apply(node.getSelect(), context);

      final Relation from = (Relation) rewriter.apply(node.getFrom(), context);

      final Optional<WindowExpression> windowExpression = node.getWindow()
          .map(exp -> ((WindowExpression) rewriter.apply(exp, context)));

      final Optional<Expression> where = node.getWhere()
          .map(exp -> (processExpression(exp, context)));

      final Optional<GroupBy> groupBy = node.getGroupBy()
          .map(exp -> ((GroupBy) rewriter.apply(exp, context)));

      final Optional<Expression> having = node.getHaving()
          .map(exp -> (processExpression(exp, context)));

      return new Query(
          node.getLocation(),
          select,
          from,
          windowExpression,
          where,
          groupBy,
          having,
          node.getResultMaterialization(),
          node.isStatic(),
          node.getLimit()
      );
    }

    @Override
    protected AstNode visitExplain(final Explain node, final C context) {
      final Optional<AstNode> result = plugin.apply(node, new Context<>(context, this));
      if (result.isPresent()) {
        return result.get();
      }

      if (!node.getStatement().isPresent()) {
        return node;
      }

      final Statement original = node.getStatement().get();
      final Statement rewritten = (Statement) rewriter.apply(original, context);

      return new Explain(
          node.getLocation(),
          node.getQueryId(),
          Optional.of(rewritten)
      );
    }

    @Override
    protected AstNode visitSelect(final Select node, final C context) {
      final Optional<AstNode> result = plugin.apply(node, new Context<>(context, this));
      if (result.isPresent()) {
        return result.get();
      }

      final List<SelectItem> rewrittenItems = node.getSelectItems()
          .stream()
          .map(selectItem -> (SelectItem) rewriter.apply(selectItem, context))
          .collect(Collectors.toList());

      return new Select(
          node.getLocation(),
          rewrittenItems
      );
    }

    @Override
    protected AstNode visitSingleColumn(final SingleColumn node, final C context) {
      final Optional<AstNode> result = plugin.apply(node, new Context<>(context, this));
      if (result.isPresent()) {
        return result.get();
      }
      return node.copyWithExpression(processExpression(node.getExpression(), context));
    }

    @Override
    protected AstNode visitAllColumns(final AllColumns node, final C context) {
      final Optional<AstNode> result = plugin.apply(node, new Context<>(context, this));
      if (result.isPresent()) {
        return result.get();
      }
      return node;
    }

    @Override
    protected AstNode visitTable(final Table node, final C context) {
      return node;
    }

    @Override
    protected AstNode visitAliasedRelation(final AliasedRelation node, final C context) {
      final Optional<AstNode> result = plugin.apply(node, new Context<>(context, this));
      if (result.isPresent()) {
        return result.get();
      }

      final Relation rewrittenRelation = (Relation) rewriter.apply(node.getRelation(), context);

      return new AliasedRelation(
          node.getLocation(),
          rewrittenRelation,
          node.getAlias());
    }

    @Override
    protected AstNode visitJoin(final Join node, final C context) {
      final Optional<AstNode> result = plugin.apply(node, new Context<>(context, this));
      if (result.isPresent()) {
        return result.get();
      }

      final Relation rewrittenLeft = (Relation) rewriter.apply(node.getLeft(), context);
      final Relation rewrittenRight = (Relation) rewriter.apply(node.getRight(), context);
      final Optional<WithinExpression> rewrittenWithin = node.getWithinExpression()
          .map(within -> (WithinExpression) rewriter.apply(within, context));
      JoinCriteria rewrittenCriteria = node.getCriteria();
      if (node.getCriteria() instanceof JoinOn) {
        rewrittenCriteria = new JoinOn(
            processExpression(((JoinOn) node.getCriteria()).getExpression(), context)
        );
      }

      return new Join(
          node.getLocation(),
          node.getType(),
          rewrittenLeft,
          rewrittenRight,
          rewrittenCriteria,
          rewrittenWithin);
    }

    @Override
    protected AstNode visitWithinExpression(final WithinExpression node, final C context) {
      return node;
    }

    @Override
    protected AstNode visitWindowExpression(final WindowExpression node, final C context) {
      final Optional<AstNode> result = plugin.apply(node, new Context<>(context, this));
      if (result.isPresent()) {
        return result.get();
      }
      return new WindowExpression(
          node.getLocation(),
          node.getWindowName(),
          node.getKaypherWindowExpression()
      );
    }

    @Override
    protected AstNode visitTableElement(final TableElement node, final C context) {
      final Optional<AstNode> result = plugin.apply(node, new Context<>(context, this));
      if (result.isPresent()) {
        return result.get();
      }
      return new TableElement(
          node.getLocation(),
          node.getNamespace(),
          node.getName(),
          (Type) processExpression(node.getType(), context)
      );
    }

    @Override
    protected AstNode visitCreateStream(final CreateStream node, final C context) {
      final Optional<AstNode> result = plugin.apply(node, new Context<>(context, this));
      if (result.isPresent()) {
        return result.get();
      }

      final List<TableElement> rewrittenElements = node.getElements().stream()
          .map(tableElement -> (TableElement) rewriter.apply(tableElement, context))
          .collect(Collectors.toList());

      return node.copyWith(TableElements.of(rewrittenElements), node.getProperties());
    }

    @Override
    protected AstNode visitCreateStreamAsSelect(
        final CreateStreamAsSelect node,
        final C context) {
      final Optional<AstNode> result = plugin.apply(node, new Context<>(context, this));
      if (result.isPresent()) {
        return result.get();
      }

      final Optional<Expression> partitionBy = node.getPartitionByColumn()
          .map(exp -> processExpression(exp, context));

      return new CreateStreamAsSelect(
          node.getLocation(),
          node.getName(),
          (Query) rewriter.apply(node.getQuery(), context),
          node.isNotExists(),
          node.getProperties(),
          partitionBy
      );
    }

    @Override
    protected AstNode visitCreateTable(final CreateTable node, final C context) {
      final Optional<AstNode> result = plugin.apply(node, new Context<>(context, this));
      if (result.isPresent()) {
        return result.get();
      }

      final List<TableElement> rewrittenElements = node.getElements().stream()
          .map(tableElement -> (TableElement) rewriter.apply(tableElement, context))
          .collect(Collectors.toList());

      return node.copyWith(TableElements.of(rewrittenElements), node.getProperties());
    }

    @Override
    protected AstNode visitCreateTableAsSelect(
        final CreateTableAsSelect node,
        final C context) {
      final Optional<AstNode> result = plugin.apply(node, new Context<>(context, this));
      if (result.isPresent()) {
        return result.get();
      }

      return new CreateTableAsSelect(
          node.getLocation(),
          node.getName(),
          (Query) rewriter.apply(node.getQuery(), context),
          node.isNotExists(),
          node.getProperties()
      );
    }

    @Override
    protected AstNode visitInsertInto(final InsertInto node, final C context) {
      final Optional<AstNode> result = plugin.apply(node, new Context<>(context, this));
      if (result.isPresent()) {
        return result.get();
      }

      final Optional<Expression> rewrittenPartitionBy = node.getPartitionByColumn()
          .map(exp -> processExpression(exp, context));

      return new InsertInto(
          node.getLocation(),
          node.getTarget(),
          (Query) rewriter.apply(node.getQuery(), context),
          rewrittenPartitionBy);
    }

    @Override
    protected AstNode visitDropTable(final DropTable node, final C context) {
      return node;
    }

    @Override
    protected AstNode visitGroupBy(final GroupBy node, final C context) {
      final Optional<AstNode> result = plugin.apply(node, new Context<>(context, this));
      if (result.isPresent()) {
        return result.get();
      }

      final List<GroupingElement> rewrittenGroupings = node.getGroupingElements().stream()
          .map(groupingElement -> (GroupingElement) rewriter.apply(groupingElement, context))
          .collect(Collectors.toList());

      return new GroupBy(node.getLocation(), rewrittenGroupings);
    }

    @Override
    protected AstNode visitSimpleGroupBy(final SimpleGroupBy node, final C context) {
      final Optional<AstNode> result = plugin.apply(node, new Context<>(context, this));
      if (result.isPresent()) {
        return result.get();
      }

      final List<Expression> columns = node.getColumns().stream()
          .map(ce -> processExpression(ce, context))
          .collect(Collectors.toList());

      return new SimpleGroupBy(
          node.getLocation(),
          columns
      );
    }

    @Override
    public AstNode visitRegisterType(final RegisterType node, final C context) {
      final Optional<AstNode> result = plugin.apply(node, new Context<>(context, this));
      if (result.isPresent()) {
        return result.get();
      }

      return new RegisterType(
          node.getLocation(),
          node.getName(),
          (Type) processExpression(node.getType(), context)
      );
    }
  }
}