/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.api.internal.changedetection.changes;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import org.gradle.api.Describable;
import org.gradle.api.NonNullApi;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.TaskArtifactState;
import org.gradle.api.internal.changedetection.TaskArtifactStateRepository;
import org.gradle.api.internal.tasks.ContextAwareTaskAction;
import org.gradle.api.internal.tasks.execution.TaskProperties;
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.internal.classloader.ClassLoaderHierarchyHasher;
import org.gradle.internal.execution.history.AfterPreviousExecutionState;
import org.gradle.internal.execution.history.BeforeExecutionState;
import org.gradle.internal.execution.history.ExecutionHistoryStore;
import org.gradle.internal.execution.history.OutputFilesRepository;
import org.gradle.internal.execution.history.changes.DefaultExecutionStateChanges;
import org.gradle.internal.execution.history.changes.ExecutionStateChanges;
import org.gradle.internal.execution.history.changes.OutputFileChanges;
import org.gradle.internal.execution.history.impl.DefaultBeforeExecutionState;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprinterRegistry;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.ValueSnapshotter;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

@NonNullApi
public class DefaultTaskArtifactStateRepository implements TaskArtifactStateRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultTaskArtifactStateRepository.class);

    private final FileCollectionFingerprinterRegistry fingerprinterRegistry;
    private final ClassLoaderHierarchyHasher classLoaderHierarchyHasher;
    private final ValueSnapshotter valueSnapshotter;
    private final ExecutionHistoryStore executionHistoryStore;
    private final OutputFilesRepository outputFilesRepository;

    public DefaultTaskArtifactStateRepository(
        FileCollectionFingerprinterRegistry fingerprinterRegistry,
        ClassLoaderHierarchyHasher classLoaderHierarchyHasher,
        ValueSnapshotter valueSnapshotter,
        ExecutionHistoryStore executionHistoryStore,
        OutputFilesRepository outputFilesRepository
    ) {
        this.fingerprinterRegistry = fingerprinterRegistry;
        this.classLoaderHierarchyHasher = classLoaderHierarchyHasher;
        this.valueSnapshotter = valueSnapshotter;
        this.executionHistoryStore = executionHistoryStore;
        this.outputFilesRepository = outputFilesRepository;
    }

    public TaskArtifactState getStateFor(final TaskInternal task, TaskProperties taskProperties) {
        return new TaskArtifactStateImpl(task, taskProperties);
    }

    private class TaskArtifactStateImpl implements TaskArtifactState {
        private final TaskInternal task;
        private final TaskProperties taskProperties;

        private boolean outputsRemoved;
        private boolean statesCalculated;
        private ImmutableSortedMap<String, CurrentFileCollectionFingerprint> outputFilesBeforeExecution;
        private BeforeExecutionState beforeExecutionState;
        private ExecutionStateChanges states;

        public TaskArtifactStateImpl(TaskInternal task, TaskProperties taskProperties) {
            this.task = task;
            this.taskProperties = taskProperties;
        }

        @Override
        public boolean isAllowedToUseCachedResults() {
            return true;
        }

        @Override
        public void afterOutputsRemovedBeforeTask() {
            outputsRemoved = true;
        }

        @Override
        public void persistNewOutputs(@Nullable AfterPreviousExecutionState afterPreviousExecutionState, ImmutableSortedMap<String, CurrentFileCollectionFingerprint> newOutputFingerprints, boolean successful, OriginMetadata originMetadata) {
            // Only persist history if there was no failure, or some output files have been changed
            if (successful || afterPreviousExecutionState == null || hasAnyOutputFileChanges(afterPreviousExecutionState.getOutputFileProperties(), newOutputFingerprints)) {
                BeforeExecutionState execution = getBeforeExecutionState(afterPreviousExecutionState).get();
                executionHistoryStore.store(
                    task.getPath(),
                    OriginMetadata.fromPreviousBuild(originMetadata.getBuildInvocationId(), originMetadata.getExecutionTime()),
                    execution.getImplementation(),
                    execution.getAdditionalImplementations(),
                    execution.getInputProperties(),
                    execution.getInputFileProperties(),
                    newOutputFingerprints,
                    successful
                );

                outputFilesRepository.recordOutputs(newOutputFingerprints.values());
            }
        }

        private boolean hasAnyOutputFileChanges(ImmutableSortedMap<String, FileCollectionFingerprint> previous, ImmutableSortedMap<String, CurrentFileCollectionFingerprint> current) {
            return !previous.keySet().equals(current.keySet())
                || new OutputFileChanges(previous, current).hasAnyChanges();
        }

        @Override
        public Optional<ExecutionStateChanges> getExecutionStateChanges(final @Nullable AfterPreviousExecutionState afterPreviousExecutionState) {
            if (!statesCalculated) {
                statesCalculated = true;
                // Calculate initial state - note this is potentially expensive
                // We need to evaluate this even if we have no history, since every input property should be evaluated before the task executes
                Optional<BeforeExecutionState> beforeExecutionState = getBeforeExecutionState(afterPreviousExecutionState);
                if (afterPreviousExecutionState == null || outputsRemoved) {
                    states = null;
                } else {
                    // TODO We need a nicer describable wrapper around task here
                    states = beforeExecutionState.map(new Function<BeforeExecutionState, DefaultExecutionStateChanges>() {
                        @Override
                        public DefaultExecutionStateChanges apply(BeforeExecutionState beforeExecution) {
                            return new DefaultExecutionStateChanges(afterPreviousExecutionState, beforeExecution, new Describable() {
                                @Override
                                public String getDisplayName() {
                                    // The value is cached, so we should be okay to call this many times
                                    return task.toString();
                                }
                            });
                        }
                    }).orElse(null);
                }
            }
            return Optional.ofNullable(states);
        }

        @Override
        public ImmutableSortedMap<String, CurrentFileCollectionFingerprint> getOutputFilesBeforeExecution() {
            if (outputFilesBeforeExecution == null) {
                outputFilesBeforeExecution = snapshotOutputs(task, taskProperties);
            }
            return outputFilesBeforeExecution;
        }

        @Override
        public Optional<BeforeExecutionState> getBeforeExecutionState(@Nullable AfterPreviousExecutionState afterPreviousExecutionState) {
            if (beforeExecutionState == null) {
                beforeExecutionState = createExecution(task, taskProperties, afterPreviousExecutionState, getOutputFilesBeforeExecution());
            }
            return Optional.of(beforeExecutionState);
        }
    }

    private ImmutableSortedMap<String, CurrentFileCollectionFingerprint> snapshotOutputs(TaskInternal task, TaskProperties taskProperties) {
        return TaskFingerprintUtil.fingerprintTaskFiles(task, taskProperties.getOutputFileProperties(), fingerprinterRegistry);
    }

    private BeforeExecutionState createExecution(TaskInternal task, TaskProperties taskProperties, @Nullable AfterPreviousExecutionState afterPreviousExecutionState, ImmutableSortedMap<String, CurrentFileCollectionFingerprint> outputFiles) {
        Class<? extends TaskInternal> taskClass = task.getClass();
        List<ContextAwareTaskAction> taskActions = task.getTaskActions();
        ImplementationSnapshot taskImplementation = ImplementationSnapshot.of(taskClass, classLoaderHierarchyHasher);
        ImmutableList<ImplementationSnapshot> taskActionImplementations = collectActionImplementations(taskActions, classLoaderHierarchyHasher);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Implementation for {}: {}", task, taskImplementation);
            LOGGER.debug("Action implementations for {}: {}", task, taskActionImplementations);
        }

        @SuppressWarnings("RedundantTypeArguments")
        ImmutableSortedMap<String, ValueSnapshot> previousInputProperties = afterPreviousExecutionState == null ? ImmutableSortedMap.<String, ValueSnapshot>of() : afterPreviousExecutionState.getInputProperties();
        ImmutableSortedMap<String, ValueSnapshot> inputProperties = snapshotTaskInputProperties(task, taskProperties, previousInputProperties, valueSnapshotter);

        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> inputFiles = TaskFingerprintUtil.fingerprintTaskFiles(task, taskProperties.getInputFileProperties(), fingerprinterRegistry);

        return new DefaultBeforeExecutionState(
            taskImplementation,
            taskActionImplementations,
            inputProperties,
            inputFiles,
            outputFiles
        );
    }

    private static ImmutableList<ImplementationSnapshot> collectActionImplementations(Collection<ContextAwareTaskAction> taskActions, ClassLoaderHierarchyHasher classLoaderHierarchyHasher) {
        if (taskActions.isEmpty()) {
            return ImmutableList.of();
        }
        ImmutableList.Builder<ImplementationSnapshot> actionImplementations = ImmutableList.builder();
        for (ContextAwareTaskAction taskAction : taskActions) {
            actionImplementations.add(taskAction.getActionImplementation(classLoaderHierarchyHasher));
        }
        return actionImplementations.build();
    }

    private static ImmutableSortedMap<String, ValueSnapshot> snapshotTaskInputProperties(TaskInternal task, TaskProperties taskProperties, ImmutableSortedMap<String, ValueSnapshot> previousInputProperties, ValueSnapshotter valueSnapshotter) {
        ImmutableSortedMap.Builder<String, ValueSnapshot> builder = ImmutableSortedMap.naturalOrder();
        Map<String, Object> inputPropertyValues = taskProperties.getInputPropertyValues().create();
        assert inputPropertyValues != null;
        for (Map.Entry<String, Object> entry : inputPropertyValues.entrySet()) {
            String propertyName = entry.getKey();
            Object value = entry.getValue();
            try {
                ValueSnapshot previousSnapshot = previousInputProperties.get(propertyName);
                if (previousSnapshot == null) {
                    builder.put(propertyName, valueSnapshotter.snapshot(value));
                } else {
                    builder.put(propertyName, valueSnapshotter.snapshot(value, previousSnapshot));
                }
            } catch (Exception e) {
                throw new UncheckedIOException(String.format("Unable to store input properties for %s. Property '%s' with value '%s' cannot be serialized.", task, propertyName, value), e);
            }
        }

        return builder.build();
    }
}
