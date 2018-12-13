/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.tasks;

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.api.internal.changedetection.TaskArtifactState;
import org.gradle.api.internal.tasks.execution.TaskProperties;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.caching.internal.tasks.TaskOutputCachingBuildCacheKey;
import org.gradle.execution.plan.LocalTaskNode;
import org.gradle.internal.execution.history.AfterPreviousExecutionState;
import org.gradle.internal.execution.history.BeforeExecutionState;
import org.gradle.internal.execution.history.changes.ExecutionStateChanges;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

public interface TaskExecutionContext {

    LocalTaskNode getLocalTaskNode();

    @Nullable
    AfterPreviousExecutionState getAfterPreviousExecution();

    void setAfterPreviousExecution(@Nullable AfterPreviousExecutionState previousExecution);

    TaskArtifactState getTaskArtifactState();

    Optional<BeforeExecutionState> getBeforeExecutionState();

    void setBeforeExecutionState(BeforeExecutionState beforeExecutionState);

    void setTaskArtifactState(TaskArtifactState taskArtifactState);

    ImmutableSortedMap<String, CurrentFileCollectionFingerprint> getOutputFilesBeforeExecution();

    void setOutputFilesBeforeExecution(ImmutableSortedMap<String, CurrentFileCollectionFingerprint> outputFilesBeforeExecution);

    TaskOutputCachingBuildCacheKey getBuildCacheKey();

    void setBuildCacheKey(TaskOutputCachingBuildCacheKey cacheKey);

    /**
     * Sets the execution time of the task to be the elapsed time since start to now.
     *
     * This is _only_ used for origin time tracking. It is not used to report the time taken in _this_ build.
     * If the outputs from this execution are reused, this time will be considered to be the origin execution time.
     *
     * This time includes from the very start of the task (e.g. include input snapshotting), the task actions, and output snapshotting.
     * It does not include time taken to write back to the build cache, or time to update the task history repository.
     *
     * This can only be called once per task.
     */
    long markExecutionTime();

    /**
     * The previously marked execution time.
     *
     * Throws if the execution time was not previously marked.
     */
    long getExecutionTime();

    @Nullable
    List<String> getUpToDateMessages();

    void setUpToDateMessages(List<String> upToDateMessages);

    void setTaskProperties(TaskProperties taskProperties);

    TaskProperties getTaskProperties();

    /**
     * Returns if caching for this task is enabled.
     */
    boolean isTaskCachingEnabled();

    void setTaskCachingEnabled(boolean enabled);

    /**
     * Returns if this task was executed incrementally.
     *
     * @see IncrementalTaskInputs#isIncremental()
     */
    boolean isTaskExecutedIncrementally();

    void setTaskExecutedIncrementally(boolean taskExecutedIncrementally);

    boolean isOutputRemovedBeforeExecution();

    void setOutputRemovedBeforeExecution(boolean outputRemovedBeforeExecution);

    Optional<ExecutionStateChanges> getExecutionStateChanges();

    void setExecutionStateChanges(ExecutionStateChanges executionStateChanges);
}
