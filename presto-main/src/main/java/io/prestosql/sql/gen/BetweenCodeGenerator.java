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
package io.prestosql.sql.gen;

import io.airlift.bytecode.BytecodeBlock;
import io.airlift.bytecode.BytecodeNode;
import io.airlift.bytecode.Variable;
import io.airlift.bytecode.instruction.LabelNode;
import io.prestosql.spi.function.FunctionHandle;
import io.prestosql.spi.relation.RowExpression;
import io.prestosql.spi.relation.SpecialForm;
import io.prestosql.spi.relation.VariableReferenceExpression;
import io.prestosql.spi.type.Type;
import io.prestosql.sql.analyzer.TypeSignatureProvider;

import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static io.prestosql.spi.function.OperatorType.GREATER_THAN_OR_EQUAL;
import static io.prestosql.spi.function.OperatorType.LESS_THAN_OR_EQUAL;
import static io.prestosql.spi.relation.SpecialForm.Form.BETWEEN_AND;
import static io.prestosql.spi.type.BooleanType.BOOLEAN;
import static io.prestosql.sql.gen.BytecodeUtils.ifWasNullPopAndGoto;
import static io.prestosql.sql.gen.RowExpressionCompiler.createTempVariableReferenceExpression;
import static io.prestosql.sql.relational.Expressions.call;

public class BetweenCodeGenerator
        implements BytecodeGenerator
{
    @Override
    public BytecodeNode generateExpression(FunctionHandle functionHandle, BytecodeGeneratorContext generatorContext, Type returnType, List<RowExpression> arguments)
    {
        checkArgument(functionHandle == null, "functionHandle is null here");

        RowExpression value = arguments.get(0);
        RowExpression min = arguments.get(1);
        RowExpression max = arguments.get(2);

        Variable firstValue = generatorContext.getScope().createTempVariable(value.getType().getJavaType());
        VariableReferenceExpression valueReference = createTempVariableReferenceExpression(firstValue, value.getType());

        SpecialForm newExpression = new SpecialForm(
                BETWEEN_AND,
                BOOLEAN,
                call(
                        GREATER_THAN_OR_EQUAL.getFunctionName().getObjectName(),
                        generatorContext.getFunctionManager().resolveOperatorFunctionHandle(GREATER_THAN_OR_EQUAL, TypeSignatureProvider.fromTypes(value.getType(), min.getType())),
                        BOOLEAN,
                        valueReference,
                        min),
                call(
                        LESS_THAN_OR_EQUAL.getFunctionName().getObjectName(),
                        generatorContext.getFunctionManager().resolveOperatorFunctionHandle(LESS_THAN_OR_EQUAL, TypeSignatureProvider.fromTypes(value.getType(), min.getType())),
                        BOOLEAN,
                        valueReference,
                        max));

        LabelNode done = new LabelNode("done");

        // push value arg on the stack
        BytecodeBlock block = new BytecodeBlock()
                .comment("check if value is null")
                .append(generatorContext.generate(value))
                .append(ifWasNullPopAndGoto(generatorContext.getScope(), done, boolean.class, value.getType().getJavaType()))
                .putVariable(firstValue)
                .append(generatorContext.generate(newExpression))
                .visitLabel(done);

        return block;
    }
}
