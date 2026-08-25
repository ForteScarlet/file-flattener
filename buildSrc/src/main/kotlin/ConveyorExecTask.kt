import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.process.ExecOperations
import java.io.File
import javax.inject.Inject

/**
 * 执行 Conveyor 命令的自定义任务。
 *
 * 用法示例：
 * ```kotlin
 * tasks.register<ConveyorExecTask>("convey") {
 *     conveyorExecutable.set(project.resolveConveyorExecutable())
 *     configFile.set("conveyor.conf")
 *     outputDirectory.set(layout.buildDirectory.dir("packages"))
 * }
 * ```
 */
abstract class ConveyorExecTask @Inject constructor(
    private val layout: ProjectLayout,
    private val execOperations: ExecOperations,
    private val fileSystemOperations: FileSystemOperations
) : DefaultTask() {

    init {
        group = "conveyor"
        description = "执行 Conveyor 打包命令"
    }

    /**
     * Conveyor 可执行文件路径。
     * 必须在配置阶段设置，通常使用 `project.resolveConveyorExecutable()` 解析。
     */
    @get:InputFile
    abstract val conveyorExecutable: RegularFileProperty

    /**
     * Conveyor 配置文件路径。
     * 如果不设置，则使用默认的 conveyor.conf。
     */
    @get:InputFile
    @get:Optional
    abstract val configFile: RegularFileProperty

    /**
     * 输出目录，默认为 build/packages。
     */
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    /**
     * 额外的命令行参数。
     */
    @get:Input
    @get:Optional
    abstract val extraArgs: ListProperty<String>

    /**
     * Conveyor 子命令，默认为 "site"。
     */
    @get:Input
    abstract val subCommand: Property<String>

    /**
     * 执行失败后的最大重试次数（不含首次执行）。
     *
     * Conveyor 在生成 "site" 时会尝试从 `app.site.base-url`（例如 GitHub Pages）下载历史版本的安装包，
     * 用于生成增量更新（delta）文件。该下载偶发会因为站点临时不可用（如 503）而失败，
     * 属于瞬时性网络问题，重试通常即可恢复，因此这里加入了重试机制。
     */
    @get:Input
    abstract val maxRetries: Property<Int>

    /**
     * 每次重试前的等待时间（秒）。
     */
    @get:Input
    abstract val retryDelaySeconds: Property<Long>

    init {
        outputDirectory.convention(layout.buildDirectory.dir("packages"))
        subCommand.convention("site")
        extraArgs.convention(emptyList())
        maxRetries.convention(2)
        retryDelaySeconds.convention(10)
    }

    @TaskAction
    fun execute() {
        val javaHome = File(System.getProperty("java.home"))
        val outputDir = outputDirectory.get().asFile

        // Conveyor 默认使用 SAFE_REPLACE：当输出目录内容被改动时会拒绝覆盖。
        // build/ 下的产物可安全重建，因此先清理输出目录，避免 "output dir changed" 导致构建失败。
        fileSystemOperations.delete {
            delete(outputDir)
        }

        val conveyor = conveyorExecutable.get().asFile

        val commandLineArgs = buildList {
            add(conveyor.absolutePath)

            configFile.orNull?.let { config ->
                add("-f")
                add(config)
            }

            add("--console=plain")
            add("--show-log=error")
            add("make")
            add("--output-dir")
            add(outputDir.absolutePath)
            add(subCommand.get())

            addAll(extraArgs.get())
        }

        val attempts = maxRetries.get() + 1
        val delaySeconds = retryDelaySeconds.get()
        for (attempt in 1..attempts) {
            if (attempt > 1) {
                // 清理上一次失败留下的部分产物，避免 conveyor 因 "output dir changed" 拒绝覆盖。
                fileSystemOperations.delete {
                    delete(outputDir)
                }
            }

            val result = execOperations.exec {
                workingDir(layout.projectDirectory)
                environment("JAVA_HOME", javaHome.absolutePath)
                commandLine(commandLineArgs)
                standardOutput = System.out
                errorOutput = System.err
                isIgnoreExitValue = true
            }

            if (result.exitValue == 0) {
                return
            }

            if (attempt == attempts) {
                result.assertNormalExitValue()
            }

            logger.warn(
                "Conveyor 执行失败（第 $attempt/$attempts 次尝试，退出码 ${result.exitValue}），" +
                    "${delaySeconds}s 后重试……"
            )
            Thread.sleep(delaySeconds * 1000)
        }
    }
}
