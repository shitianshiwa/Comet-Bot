package io.github.starwishsama.nbot.commands

import io.github.starwishsama.nbot.BotConstants
import io.github.starwishsama.nbot.BotInstance
import io.github.starwishsama.nbot.commands.interfaces.UniversalCommand
import io.github.starwishsama.nbot.objects.BotUser
import net.mamoe.mirai.message.ContactMessage
import net.mamoe.mirai.message.data.EmptyMessageChain
import net.mamoe.mirai.message.data.MessageChain

/**
 * Mirai 命令处理器
 * 处理群聊/私聊聊天信息中存在的命令
 * @author Nameless
 */
class CommandHandler {
    companion object {
        var commands: List<UniversalCommand> = mutableListOf()
    }

    /**
     * 注册命令
     *
     * @param command 要注册的命令
     */
    fun setupCommand(command: UniversalCommand){
        commands = commands + command
    }

    /**
     * 注册命令
     *
     * @param commands 要注册的命令集合
     */
    fun setupCommand(commands: Array<UniversalCommand>){
        CommandHandler.commands += commands
    }

    /**
     * 执行消息中的命令
     *
     * @param message 消息
     */
    suspend fun execute(message: ContactMessage): MessageChain {
        val cmdPrefix = getCmdPrefix(message.message.contentToString())
        for (cmd in commands){
            if (isPrefix(cmd, cmdPrefix)){
                BotInstance.logger.debug("[命令] " + message.sender.id + " 执行了命令: " + cmd.getProps().name)
                var user = BotUser.getUser(message.sender.id)
                if (user == null){
                    user = BotUser.quickRegister(message.sender.id)
                }

                if (user.compareLevel(cmd.getProps().level) || user.hasPermission(cmd.getProps().permission)) {
                    val splitMessage = message.message.contentToString().split(" ")
                    return cmd.execute(message, splitMessage.subList(1, splitMessage.size), user)
                }
            }
        }
        return EmptyMessageChain
    }

    private fun getCmdPrefix(command: String): String {
        val cmdPrefix: String
        var parseTemp = ""
        for (string : String in BotConstants.cfg.commandPrefix){
            parseTemp = command.replace(string, "")
        }

        cmdPrefix = parseTemp.split(" ")[0].substring(1)
        return cmdPrefix
    }

    private fun isPrefix(cmd: UniversalCommand, prefix: String): Boolean {
        val props = cmd.getProps()
        when {
            props.name == prefix -> {
                return true
            }
            props.aliases != null -> {
                for (pre in props.aliases!!) {
                    if (pre == prefix) {
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

    fun getInstance(): CommandHandler {
        return this
    }
}