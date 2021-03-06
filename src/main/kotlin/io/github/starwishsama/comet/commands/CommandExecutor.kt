package io.github.starwishsama.comet.commands

import io.github.starwishsama.comet.BotVariables
import io.github.starwishsama.comet.commands.interfaces.ChatCommand
import io.github.starwishsama.comet.commands.interfaces.ConsoleCommand
import io.github.starwishsama.comet.commands.interfaces.SuspendCommand
import io.github.starwishsama.comet.managers.GroupConfigManager
import io.github.starwishsama.comet.objects.BotUser
import io.github.starwishsama.comet.sessions.DaemonSession
import io.github.starwishsama.comet.sessions.Session
import io.github.starwishsama.comet.sessions.SessionManager
import io.github.starwishsama.comet.utils.BotUtil
import io.github.starwishsama.comet.utils.convertToChain
import net.mamoe.mirai.message.GroupMessageEvent
import net.mamoe.mirai.message.MessageEvent
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.utils.asHumanReadable
import java.time.Duration
import java.time.LocalDateTime
import java.util.*
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.toKotlinDuration

/**
 * 彗星 Bot 命令处理器
 *
 * 处理群聊/私聊聊天信息中存在的命令
 * @author Nameless
 */
object CommandExecutor {
    private var commands: MutableSet<ChatCommand> = mutableSetOf()
    private var consoleCommands = mutableListOf<ConsoleCommand>()

    /**
     * 注册命令
     *
     * @param command 要注册的命令
     */
    private fun setupCommand(command: ChatCommand) {
        if (!commands.contains(command)) {
            commands.add(command)
        } else {
            BotVariables.logger.warning("[命令] 正在尝试注册已有命令 ${command.getProps().name}")
        }
    }

    /**
     * 注册命令
     *
     * @param commands 要注册的命令集合
     */
    fun setupCommand(commands: Array<ChatCommand>) {
        commands.forEach {
            if (!this.commands.contains(it)) {
                this.commands.add(it)
            }
        }
    }

    fun setupCommand(commands: Array<Any>) {
        commands.forEach {
            when (it) {
                is ChatCommand -> setupCommand(it)
                is ConsoleCommand ->
                    if (!consoleCommands.contains(it)) {
                        consoleCommands.plusAssign(it)
                    } else {
                        BotVariables.logger.warning("[命令] 正在尝试注册已有命令 ${it.getProps().name}")
                    }
                else -> {
                    BotVariables.logger.warning("[命令] 正在尝试注册非命令类 ${it.javaClass.simpleName}")
                }
            }
        }
    }

    /**
     * 执行消息中的命令
     *
     * @param event Mirai 消息命令 (聊天)
     */
    @ExperimentalTime
    suspend fun execute(event: MessageEvent): ExecutedResult {
        val executedTime = LocalDateTime.now()
        val senderId = event.sender.id
        val message = event.message.contentToString()
        val cmd = getCommand(getCommandName(message))

        if (BotVariables.switch || (cmd != null && cmd.getProps().name.contentEquals("debug"))) {
            try {
                val session = SessionManager.getSessionByEvent(event)
                if (session != null && !handleSession(event, executedTime)) {
                    return ExecutedResult(EmptyMessageChain, cmd)
                }

                if (cmd != null && event is GroupMessageEvent &&
                        GroupConfigManager.getConfigSafely(event.group.id).isDisabledCommand(cmd)) {
                    return ExecutedResult(EmptyMessageChain, cmd)
                }

                if (isCommandPrefix(message) && cmd != null) {
                    val splitMessage = message.split(" ")
                    BotVariables.logger.debug("[命令] $senderId 尝试执行命令: ${cmd.getProps().name}")
                    var user = BotUser.getUser(senderId)
                    if (user == null) {
                        user = BotUser.quickRegister(senderId)
                    }

                    val result: MessageChain = if (cmd.hasPermission(user, event)) {
                        cmd.execute(event, splitMessage.subList(1, splitMessage.size), user)
                    } else {
                        BotUtil.sendMessage("你没有权限!")
                    }
                    val usedTime = Duration.between(executedTime, LocalDateTime.now())
                    BotVariables.logger.debug(
                            "[命令] 命令执行耗时 ${usedTime.toKotlinDuration().asHumanReadable}"
                    )

                    return ExecutedResult(result, cmd)
                }
            } catch (t: Throwable) {
                val msg = t.message
                return if (msg != null && msg.contains("time")) {
                    ExecutedResult("Bot > 在执行网络操作时连接超时".convertToChain(), cmd)
                } else {
                    BotVariables.logger.warning("[命令] 在试图执行命令时发生了一个错误, 原文: $message, 发送者: $senderId", t)
                    ExecutedResult("Bot > 在试图执行命令时发生了一个错误, 请联系管理员".convertToChain(), cmd)
                }
            }
        }
        return ExecutedResult(EmptyMessageChain, cmd)
    }

    /**
     * 执行后台命令
     *
     * @param content 纯文本命令 (后台)
     */
    suspend fun executeConsole(content: String): String {
        try {
            if (isCommandPrefix(content)) {
                val cmd = getConsoleCommand(getCommandName(content))
                if (cmd != null) {
                    val splitMessage = content.split(" ")
                    val splitCommand = splitMessage.subList(1, splitMessage.size)
                    BotVariables.logger.debug("[命令] 后台尝试执行命令: " + cmd.getProps().name)
                    return cmd.execute(splitCommand)
                }
            }
        } catch (t: Throwable) {
            BotVariables.logger.warning("[命令] 在试图执行命令时发生了一个错误, 原文: $content", t)
            return ""
        }
        return ""
    }

    /**
     * 处理会话
     * @return 是否为监听会话
     */
    @ExperimentalTime
    private suspend fun handleSession(event: MessageEvent, time: LocalDateTime): Boolean {
        val sender = event.sender
        if (!isCommandPrefix(event.message.contentToString())) {
            val session: Session? = SessionManager.getSessionByEvent(event)
            if (session != null) {
                val command = session.command
                if (command is SuspendCommand) {
                    val user = BotUser.getUserSafely(sender.id)
                    command.handleInput(event, user, session)
                }

                return session is DaemonSession
            }
        }

        val usedTime = Duration.between(time, LocalDateTime.now())
        BotVariables.logger.debug(
                "[会话] 处理会话耗时 ${usedTime.toKotlinDuration().toLong(DurationUnit.SECONDS)}s${usedTime.toKotlinDuration()
                        .toLong(DurationUnit.MILLISECONDS)}ms"
        )
        return true
    }

    fun getCommand(cmdPrefix: String): ChatCommand? {
        for (command in commands) {
            if (commandEquals(command, cmdPrefix)) {
                return command
            }
        }
        return null
    }

    private fun getConsoleCommand(cmdPrefix: String): ConsoleCommand? {
        for (command in consoleCommands) {
            if (commandEquals(command, cmdPrefix)) {
                return command
            }
        }
        return null
    }

    private fun getCommandName(command: String): String {
        var cmdPrefix = command
        for (string: String in BotVariables.cfg.commandPrefix) {
            cmdPrefix = cmdPrefix.replace(string, "")
        }

        return cmdPrefix.split(" ")[0]
    }

    private fun isCommandPrefix(message: String): Boolean {
        if (message.isNotEmpty()) {
            BotVariables.cfg.commandPrefix.forEach {
                if (message.startsWith(it)) {
                    return true
                }
            }
        }

        return false
    }

    private fun commandEquals(cmd: ChatCommand, cmdName: String): Boolean {
        val props = cmd.getProps()
        when {
            props.name.contentEquals(cmdName) -> {
                return true
            }
            props.aliases != null -> {
                props.aliases?.forEach {
                    if (it.contentEquals(cmdName)) {
                        return true
                    }
                }
            }
            else -> {
                return false
            }
        }
        return false
    }

    private fun commandEquals(cmd: ConsoleCommand, cmdName: String): Boolean {
        val props = cmd.getProps()
        when {
            props.name.contentEquals(cmdName) -> {
                return true
            }
            props.aliases != null -> {
                props.aliases?.forEach {
                    if (it.contentEquals(cmdName)) {
                        return true
                    }
                }
            }
            else -> {
                return false
            }
        }
        return false
    }

    fun MessageChain.doFilter(): MessageChain {
        if (BotVariables.cfg.filterWords.isNullOrEmpty()) {
            return this
        }

        val revampChain = LinkedList<SingleMessage>()
        this.forEach { revampChain.add(it) }

        var count = 0

        for (i in revampChain.indices) {
            if (revampChain[i] is PlainText) {
                var context = revampChain[i].content
                BotVariables.cfg.filterWords.forEach {
                    if (context.contains(it)) {
                        count++
                        context = context.replace(it.toRegex(), " ")
                    }

                    if (count > 5) {
                        return EmptyMessageChain
                    }
                }
                revampChain[i] = PlainText(context)
            }
        }

        return revampChain.asMessageChain()
    }

    fun countCommands(): Int = commands.size + consoleCommands.size

    fun getCommands() = commands

    data class ExecutedResult(val msg: MessageChain, val cmd: ChatCommand?)
}