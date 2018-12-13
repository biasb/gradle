/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.tasks.execution;

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskExecuterResult;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.internal.execution.history.AfterPreviousExecutionState;
import org.gradle.internal.execution.history.changes.ExecutionStateChanges;

import javax.annotation.Nullable;
import java.util.function.Consumer;

public class ResolveChangesTaskExecuter implements TaskExecuter {
    private final TaskExecuter delegate;

    public ResolveChangesTaskExecuter(TaskExecuter delegate) {
        this.delegate = delegate;
    }

    @Nullable
    @Override
    public TaskExecuterResult execute(TaskInternal task, TaskStateInternal state, final TaskExecutionContext context) {
        AfterPreviousExecutionState afterPreviousExecution = context.getAfterPreviousExecution();
        boolean outputRemovedBeforeExecution = context.isOutputRemovedBeforeExecution();
        context.getTaskArtifactState().getExecutionStateChanges(afterPreviousExecution, outputRemovedBeforeExecution).ifPresent(new Consumer<ExecutionStateChanges>() {
            @Override
            public void accept(ExecutionStateChanges executionStateChanges) {
                context.setExecutionStateChanges(executionStateChanges);
            }
        });
        return delegate.execute(task, state, context);
    }
}
