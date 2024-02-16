package teksturepako.pakku.cli.cmd

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.mordant.terminal.YesNoPrompt
import kotlinx.coroutines.runBlocking
import teksturepako.pakku.api.actions.createAdditionRequest
import teksturepako.pakku.api.data.PakkuLock
import teksturepako.pakku.api.platforms.Platform
import teksturepako.pakku.cli.promptForProject
import teksturepako.pakku.cli.resolveDependencies

class Add : CliktCommand("Add projects")
{
    private val projectArgs: List<String> by argument("projects").multiple(required = true)

    override fun run() = runBlocking {
        val pakkuLock = PakkuLock.readToResult().getOrElse {
            terminal.danger(it.message)
            echo()
            return@runBlocking
        }

        // Configuration
        val platforms: List<Platform> = pakkuLock.getPlatforms().getOrElse {
            terminal.danger(it.message)
            echo()
            return@runBlocking
        }

        val projectProvider = pakkuLock.getProjectProvider().getOrElse {
            terminal.danger(it.message)
            echo()
            return@runBlocking
        }
        // --

        for (projectIn in projectArgs.map { arg ->
            projectProvider.requestProjectWithFiles(pakkuLock.getMcVersions(), pakkuLock.getLoaders(), arg)
        })
        {
            projectIn.createAdditionRequest(
                onError = { error -> terminal.danger(error.message) },
                onRetry = { platform -> promptForProject(platform, terminal, pakkuLock) },
                onSuccess = { project, isRecommended, reqHandlers ->
                    if (YesNoPrompt("Do you want to add ${project.slug}?", terminal, isRecommended).ask() == true)
                    {
                        pakkuLock.add(project)
                        pakkuLock.linkProjectToDependants(project)
                        project.resolveDependencies(terminal, reqHandlers, pakkuLock, projectProvider, platforms)
                        terminal.success("${project.slug} added")
                    }
                },
                pakkuLock, platforms
            )

            echo()
        }
        pakkuLock.write()
    }
}