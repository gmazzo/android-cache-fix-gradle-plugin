package org.gradle.android.workarounds.room

import org.gradle.android.workarounds.room.argumentprovider.KspRoomSchemaLocationArgumentProvider
import org.gradle.android.workarounds.room.task.RoomSchemaLocationMergeTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.file.Directory
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider

class KspWorkaround extends AnnotationProcessorWorkaround<KspRoomSchemaLocationArgumentProvider> {
    public static final String KSP_TASK = "com.google.devtools.ksp.gradle.KspTaskJvm_Decorated"

    KspWorkaround(Project project, RoomExtension extension, TaskProvider<RoomSchemaLocationMergeTask> mergeTask) {
        super(project, extension, mergeTask)
    }

    static KspWorkaround create(Project project, RoomExtension extension, TaskProvider<RoomSchemaLocationMergeTask> mergeTask) {
        return new KspWorkaround(project, extension, mergeTask)
    }

    @Override
    void initWorkaround() {
        project.tasks.matching({ it.class.name == KSP_TASK }).configureEach {
            configureWorkaroundTask(it)
        }
    }

    @Override
    void configureWorkaroundTask(Task task) {

        def fileOperations = project.fileOperations

        addArgumentProvider(task)

        task.doFirst onlyIfKspRoomSchemaLocationArgumentProviderIsConfigured(task.commandLineArgumentProviders) { KspRoomSchemaLocationArgumentProvider provider ->
            KspWorkaround.copyExistingSchemasToTaskSpecificTmpDir(fileOperations, roomExtension.schemaLocationDir, provider)
        }

        task.doLast onlyIfKspRoomSchemaLocationArgumentProviderIsConfigured(task.commandLineArgumentProviders) { KspRoomSchemaLocationArgumentProvider provider ->
            KspWorkaround.copyGeneratedSchemasToOutput(fileOperations, provider)
        }

        task.finalizedBy onlyIfKspRoomSchemaLocationArgumentProviderIsConfigured(task.commandLineArgumentProviders) {
            roomExtension.schemaLocationDir.isPresent() ? mergeTask : null
        }

        TaskExecutionGraph taskGraph = project.gradle.taskGraph
        taskGraph.whenReady onlyIfKspRoomSchemaLocationArgumentProviderIsConfigured(task.commandLineArgumentProviders) { KspRoomSchemaLocationArgumentProvider provider ->
            if (taskGraph.hasTask(task)) {
                roomExtension.registerOutputDirectory(provider.schemaLocationDir)
            }
        }
    }

    private void addArgumentProvider(Task task) {
        if (roomExtension.schemaLocationDir.isPresent()) {
            def variantSpecificSchemaDir = project.objects.directoryProperty()
            variantSpecificSchemaDir.set(androidVariantProvider.getVariantSpecificSchemaDir(project, "${task.name}"))
            task.commandLineArgumentProviders.add(new KspRoomSchemaLocationArgumentProvider(roomExtension.schemaLocationDir, variantSpecificSchemaDir))
        }
    }

    static void copyExistingSchemasToTaskSpecificTmpDir(FileOperations fileOperations, Provider<Directory> existingSchemaDir, KspRoomSchemaLocationArgumentProvider provider) {
        if (existingSchemaDir.isPresent()) {
            def temporaryVariantSpecificSchemaDir = provider.temporarySchemaLocationDir
            fileOperations.sync {
                it.from existingSchemaDir
                it.into temporaryVariantSpecificSchemaDir
            }
        }
    }

    static void copyGeneratedSchemasToOutput(FileOperations fileOperations, KspRoomSchemaLocationArgumentProvider provider) {
        def variantSpecificSchemaDir = provider.schemaLocationDir
        def temporaryVariantSpecificSchemaDir = provider.temporarySchemaLocationDir
        fileOperations.sync {
            it.from temporaryVariantSpecificSchemaDir
            it.into variantSpecificSchemaDir
        }
    }

    private static Closure onlyIfKspRoomSchemaLocationArgumentProviderIsConfigured(def commandLineArgumentProviders, Closure<?> action) {
        return {
            def provider = commandLineArgumentProviders.get().find { it instanceof KspRoomSchemaLocationArgumentProvider }
            if (provider != null) {
                action.call(provider)
            }
        }
    }
}
