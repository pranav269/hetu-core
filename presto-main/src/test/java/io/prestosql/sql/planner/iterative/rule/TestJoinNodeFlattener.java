/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.prestosql.sql.planner.iterative.rule;

import com.google.common.collect.ImmutableList;
import io.prestosql.expressions.LogicalRowExpressions;
import io.prestosql.spi.function.BuiltInFunctionHandle;
import io.prestosql.spi.function.OperatorType;
import io.prestosql.spi.function.Signature;
import io.prestosql.spi.plan.JoinNode;
import io.prestosql.spi.plan.JoinNode.EquiJoinClause;
import io.prestosql.spi.plan.PlanNodeIdAllocator;
import io.prestosql.spi.plan.Symbol;
import io.prestosql.spi.plan.ValuesNode;
import io.prestosql.spi.relation.CallExpression;
import io.prestosql.spi.relation.RowExpression;
import io.prestosql.sql.planner.TypeProvider;
import io.prestosql.sql.planner.iterative.rule.ReorderJoins.MultiJoinNode;
import io.prestosql.sql.planner.iterative.rule.test.PlanBuilder;
import io.prestosql.sql.relational.FunctionResolution;
import io.prestosql.sql.relational.RowExpressionDeterminismEvaluator;
import io.prestosql.testing.LocalQueryRunner;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Optional;

import static io.airlift.testing.Closeables.closeAllRuntimeException;
import static io.prestosql.spi.function.Signature.internalOperator;
import static io.prestosql.spi.plan.JoinNode.Type.FULL;
import static io.prestosql.spi.plan.JoinNode.Type.INNER;
import static io.prestosql.spi.plan.JoinNode.Type.LEFT;
import static io.prestosql.spi.type.BigintType.BIGINT;
import static io.prestosql.spi.type.BooleanType.BOOLEAN;
import static io.prestosql.sql.planner.VariableReferenceSymbolConverter.toVariableReference;
import static io.prestosql.sql.planner.iterative.Lookup.noLookup;
import static io.prestosql.sql.planner.iterative.rule.ReorderJoins.MultiJoinNode.toMultiJoinNode;
import static io.prestosql.sql.relational.Expressions.call;
import static io.prestosql.sql.relational.Expressions.constant;
import static io.prestosql.testing.TestingSession.testSessionBuilder;
import static org.testng.Assert.assertEquals;

public class TestJoinNodeFlattener
{
    private static final int DEFAULT_JOIN_LIMIT = 10;

    private LocalQueryRunner queryRunner;

    private LogicalRowExpressions logicalRowExpressions;

    @BeforeClass
    public void setUp()
    {
        queryRunner = new LocalQueryRunner(testSessionBuilder().build());
        logicalRowExpressions = new LogicalRowExpressions(new RowExpressionDeterminismEvaluator(queryRunner.getMetadata()),
                                                            new FunctionResolution(queryRunner.getMetadata().getFunctionAndTypeManager()),
                                                            queryRunner.getMetadata().getFunctionAndTypeManager());
    }

    @AfterClass(alwaysRun = true)
    public void tearDown()
    {
        closeAllRuntimeException(queryRunner);
        queryRunner = null;
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testDoesNotAllowOuterJoin()
    {
        PlanBuilder p = planBuilder();
        Symbol a1 = p.symbol("A1");
        Symbol b1 = p.symbol("B1");
        JoinNode outerJoin = p.join(
                FULL,
                p.values(a1),
                p.values(b1),
                ImmutableList.of(equiJoinClause(a1, b1)),
                ImmutableList.of(a1, b1),
                Optional.empty());
        toMultiJoinNode(outerJoin, noLookup(), DEFAULT_JOIN_LIMIT, queryRunner.getMetadata(), p.getTypes(), logicalRowExpressions);
    }

    @Test
    public void testDoesNotConvertNestedOuterJoins()
    {
        PlanBuilder p = planBuilder();
        Symbol a1 = p.symbol("A1");
        Symbol b1 = p.symbol("B1");
        Symbol c1 = p.symbol("C1");
        JoinNode leftJoin = p.join(
                LEFT,
                p.values(a1),
                p.values(b1),
                ImmutableList.of(equiJoinClause(a1, b1)),
                ImmutableList.of(a1, b1),
                Optional.empty());
        ValuesNode valuesC = p.values(c1);
        JoinNode joinNode = p.join(
                INNER,
                leftJoin,
                valuesC,
                ImmutableList.of(equiJoinClause(a1, c1)),
                ImmutableList.of(a1, b1, c1),
                Optional.empty());

        MultiJoinNode expected = MultiJoinNode.builder()
                .setSources(leftJoin, valuesC)
                .setFilter(createEqualsExpression(a1, c1, p.getTypes()))
                .setOutputSymbols(a1, b1, c1)
                .setLogicalRowExpressions(logicalRowExpressions)
                .build();
        assertEquals(toMultiJoinNode(joinNode, noLookup(), DEFAULT_JOIN_LIMIT, queryRunner.getMetadata(), p.getTypes(), logicalRowExpressions), expected);
    }

    @Test
    public void testRetainsOutputSymbols()
    {
        PlanBuilder p = planBuilder();
        Symbol a1 = p.symbol("A1");
        Symbol b1 = p.symbol("B1");
        Symbol b2 = p.symbol("B2");
        Symbol c1 = p.symbol("C1");
        Symbol c2 = p.symbol("C2");
        ValuesNode valuesA = p.values(a1);
        ValuesNode valuesB = p.values(b1, b2);
        ValuesNode valuesC = p.values(c1, c2);
        JoinNode joinNode = p.join(
                INNER,
                valuesA,
                p.join(
                        INNER,
                        valuesB,
                        valuesC,
                        ImmutableList.of(equiJoinClause(b1, c1)),
                        ImmutableList.of(
                                b1,
                                b2,
                                c1,
                                c2),
                        Optional.empty()),
                ImmutableList.of(equiJoinClause(a1, b1)),
                ImmutableList.of(a1, b1),
                Optional.empty());
        MultiJoinNode expected = MultiJoinNode.builder()
                .setSources(valuesA, valuesB, valuesC)
                .setFilter(logicalRowExpressions.and(createEqualsExpression(b1, c1, p.getTypes()), createEqualsExpression(a1, b1, p.getTypes())))
                .setOutputSymbols(a1, b1)
                .setLogicalRowExpressions(logicalRowExpressions)
                .build();
        assertEquals(toMultiJoinNode(joinNode, noLookup(), DEFAULT_JOIN_LIMIT, queryRunner.getMetadata(), p.getTypes(), logicalRowExpressions), expected);
    }

    @Test
    public void testCombinesCriteriaAndFilters()
    {
        PlanBuilder p = planBuilder();
        Symbol a1 = p.symbol("A1");
        Symbol b1 = p.symbol("B1");
        Symbol b2 = p.symbol("B2");
        Symbol c1 = p.symbol("C1");
        Symbol c2 = p.symbol("C2");
        ValuesNode valuesA = p.values(a1);
        ValuesNode valuesB = p.values(b1, b2);
        ValuesNode valuesC = p.values(c1, c2);
        RowExpression bcFilter = LogicalRowExpressions.and(
                p.comparison(OperatorType.GREATER_THAN, p.variable(c2.getName()), constant(0L, BIGINT)),
                p.comparison(OperatorType.NOT_EQUAL, p.variable(c2.getName()), constant(7L, BIGINT)),
                p.comparison(OperatorType.GREATER_THAN, p.variable(b2.getName()), p.variable(c2.getName())));
        RowExpression abcFilter = p.comparison(
                OperatorType.LESS_THAN,
                p.binaryOperation(OperatorType.ADD, p.variable(a1.getName()), p.variable(c1.getName())),
                p.variable(b1.getName()));
        JoinNode joinNode = p.join(
                INNER,
                valuesA,
                p.join(
                        INNER,
                        valuesB,
                        valuesC,
                        ImmutableList.of(equiJoinClause(b1, c1)),
                        ImmutableList.of(
                                b1,
                                b2,
                                c1,
                                c2),
                        Optional.of(bcFilter)),
                ImmutableList.of(equiJoinClause(a1, b1)),
                ImmutableList.of(a1, b1, b2, c1, c2),
                Optional.of(abcFilter));
        /*
        MultiJoinNode expected = new MultiJoinNode(
                new LinkedHashSet<>(ImmutableList.of(valuesA, valuesB, valuesC)),
                and(new ComparisonExpression(EQUAL, toSymbolReference(b1), toSymbolReference(c1)), new ComparisonExpression(EQUAL, toSymbolReference(a1), toSymbolReference(b1)), bcFilter, abcFilter),
                ImmutableList.of(a1, b1, b2, c1, c2));
        assertEquals(toMultiJoinNode(joinNode, noLookup(), DEFAULT_JOIN_LIMIT), expected);
         */
    }

    @Test
    public void testConvertsBushyTrees()
    {
        PlanBuilder p = planBuilder();
        Symbol a1 = p.symbol("A1");
        Symbol b1 = p.symbol("B1");
        Symbol c1 = p.symbol("C1");
        Symbol d1 = p.symbol("D1");
        Symbol d2 = p.symbol("D2");
        Symbol e1 = p.symbol("E1");
        Symbol e2 = p.symbol("E2");
        ValuesNode valuesA = p.values(a1);
        ValuesNode valuesB = p.values(b1);
        ValuesNode valuesC = p.values(c1);
        ValuesNode valuesD = p.values(d1, d2);
        ValuesNode valuesE = p.values(e1, e2);
        JoinNode joinNode = p.join(
                INNER,
                p.join(
                        INNER,
                        p.join(
                                INNER,
                                valuesA,
                                valuesB,
                                ImmutableList.of(equiJoinClause(a1, b1)),
                                ImmutableList.of(a1, b1),
                                Optional.empty()),
                        valuesC,
                        ImmutableList.of(equiJoinClause(a1, c1)),
                        ImmutableList.of(a1, b1, c1),
                        Optional.empty()),
                p.join(
                        INNER,
                        valuesD,
                        valuesE,
                        ImmutableList.of(
                                equiJoinClause(d1, e1),
                                equiJoinClause(d2, e2)),
                        ImmutableList.of(
                                d1,
                                d2,
                                e1,
                                e2),
                        Optional.empty()),
                ImmutableList.of(equiJoinClause(b1, e1)),
                ImmutableList.of(
                        a1,
                        b1,
                        c1,
                        d1,
                        d2,
                        e1,
                        e2),
                Optional.empty());
        MultiJoinNode expected = MultiJoinNode.builder()
                .setSources(valuesA, valuesB, valuesC, valuesD, valuesE)
                .setFilter(logicalRowExpressions.and(createEqualsExpression(a1, b1, p.getTypes()),
                                                    createEqualsExpression(a1, c1, p.getTypes()),
                                                    createEqualsExpression(d1, e1, p.getTypes()),
                                                    createEqualsExpression(d2, e2, p.getTypes()),
                                                    createEqualsExpression(b1, e1, p.getTypes())))
                .setOutputSymbols(a1, b1, c1, d1, d2, e1, e2)
                .setLogicalRowExpressions(logicalRowExpressions)
                .build();
        assertEquals(toMultiJoinNode(joinNode, noLookup(), 5, queryRunner.getMetadata(), p.getTypes(), logicalRowExpressions), expected);
    }

    @Test
    public void testMoreThanJoinLimit()
    {
        PlanBuilder p = planBuilder();
        Symbol a1 = p.symbol("A1");
        Symbol b1 = p.symbol("B1");
        Symbol c1 = p.symbol("C1");
        Symbol d1 = p.symbol("D1");
        Symbol d2 = p.symbol("D2");
        Symbol e1 = p.symbol("E1");
        Symbol e2 = p.symbol("E2");
        ValuesNode valuesA = p.values(a1);
        ValuesNode valuesB = p.values(b1);
        ValuesNode valuesC = p.values(c1);
        ValuesNode valuesD = p.values(d1, d2);
        ValuesNode valuesE = p.values(e1, e2);
        JoinNode join1 = p.join(
                INNER,
                valuesA,
                valuesB,
                ImmutableList.of(equiJoinClause(a1, b1)),
                ImmutableList.of(a1, b1),
                Optional.empty());
        JoinNode join2 = p.join(
                INNER,
                valuesD,
                valuesE,
                ImmutableList.of(
                        equiJoinClause(d1, e1),
                        equiJoinClause(d2, e2)),
                ImmutableList.of(
                        d1,
                        d2,
                        e1,
                        e2),
                Optional.empty());
        JoinNode joinNode = p.join(
                INNER,
                p.join(
                        INNER,
                        join1,
                        valuesC,
                        ImmutableList.of(equiJoinClause(a1, c1)),
                        ImmutableList.of(a1, b1, c1),
                        Optional.empty()),
                join2,
                ImmutableList.of(equiJoinClause(b1, e1)),
                ImmutableList.of(
                        a1,
                        b1,
                        c1,
                        d1,
                        d2,
                        e1,
                        e2),
                Optional.empty());
        MultiJoinNode expected = MultiJoinNode.builder()
                .setSources(join1, join2, valuesC)
                .setFilter(logicalRowExpressions.and(createEqualsExpression(a1, c1, p.getTypes()), createEqualsExpression(b1, e1, p.getTypes())))
                .setOutputSymbols(a1, b1, c1, d1, d2, e1, e2)
                .setLogicalRowExpressions(logicalRowExpressions)
                .build();
        assertEquals(toMultiJoinNode(joinNode, noLookup(), 2, queryRunner.getMetadata(), p.getTypes(), logicalRowExpressions), expected);
    }

    private CallExpression createEqualsExpression(Symbol left, Symbol right, TypeProvider types)
    {
        Signature signature = internalOperator(OperatorType.EQUAL,
                BOOLEAN.getTypeSignature(),
                types.get(left).getTypeSignature(),
                types.get(right).getTypeSignature());
        return call(OperatorType.EQUAL.getFunctionName().toString(),
                new BuiltInFunctionHandle(signature),
                BOOLEAN,
                toVariableReference(left, types),
                toVariableReference(right, types));
    }

    private EquiJoinClause equiJoinClause(Symbol symbol1, Symbol symbol2)
    {
        return new EquiJoinClause(symbol1, symbol2);
    }

    private PlanBuilder planBuilder()
    {
        return new PlanBuilder(new PlanNodeIdAllocator(), queryRunner.getMetadata());
    }
}
