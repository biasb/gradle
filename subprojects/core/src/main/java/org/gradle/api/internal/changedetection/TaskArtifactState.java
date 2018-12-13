/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.changedetection;

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.execution.TaskProperties;
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.caching.internal.tasks.TaskOutputCachingBuildCacheKey;
import org.gradle.internal.execution.history.AfterPreviousExecutionState;
import org.gradle.internal.execution.history.BeforeExecutionState;
import org.gradle.internal.execution.history.changes.ExecutionStateChanges;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Encapsulates the state of the task when its outputs were last generated.
 */
public interface TaskArtifactState {
    /**
     * Returns changes since the previous execution, if any.
     */
    Optional<ExecutionStateChanges> getExecutionStateChanges(@Nullable AfterPreviousExecutionState afterPreviousExecutionState);

    /**
     * Returns whether it is okay to use results loaded from cache instead of executing the task.
     */
    boolean isAllowedToUseCachedResults();

    /**
     * Returns the calculated cache key for the task's current state.
     */
    TaskOutputCachingBuildCacheKey calculateCacheKey(@Nullable AfterPreviousExecutionState afterPreviousExecutionState, TaskProperties taskProperties);

    /**
     * Retakes output file snapshots and prevents the task from executing in an incremental fashion.
     */
    void afterOutputsRemovedBeforeTask();

    /**
     * Called on completion of task execution.
     */
    ImmutableSortedMap<String, CurrentFileCollectionFingerprint> snapshotAfterTaskExecution(TaskExecutionContext taskExecutionContext);

    /**
     * Called when outputs were generated.
     */
    void persistNewOutputs(@Nullable AfterPreviousExecutionState afterPreviousExecutionState, ImmutableSortedMap<String, CurrentFileCollectionFingerprint> newOutputFingerprints, boolean successful, OriginMetadata originMetadata);

    /**
     * Returns the outputs before execution.
     */
    ImmutableSortedMap<String, CurrentFileCollectionFingerprint> getOutputFilesBeforeExecution();

    BeforeExecutionState getBeforeExecutionState(@Nullable AfterPreviousExecutionState afterPreviousExecutionState);
}
