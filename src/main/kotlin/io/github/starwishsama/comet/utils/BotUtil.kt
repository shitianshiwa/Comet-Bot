package io.github.starwishsama.comet.utils

import cn.hutool.http.HttpResponse
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import io.github.starwishsama.comet.BotVariables
import io.github.starwishsama.comet.BotVariables.cfg
import io.github.starwishsama.comet.BotVariables.coolDown
import io.github.starwishsama.comet.enums.UserLevel
import io.github.starwishsama.comet.exceptions.RateLimitException
import io.github.starwishsama.comet.exceptions.ReachRetryLimitException
import io.github.starwishsama.comet.objects.BotUser
import io.github.starwishsama.comet.objects.BotUser.Companion.isBotAdmin
import io.github.starwishsama.comet.objects.BotUser.Companion.isBotOwner
import io.github.starwishsama.comet.utils.network.NetUtil
import net.mamoe.mirai.message.MessageEvent
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.utils.asHumanReadable
import org.apache.commons.lang3.StringUtils
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.toKotlinDuration

/**
 * 将字符串转换为消息链
 */
fun String.convertToChain(): MessageChain {
    return toMessage().asMessageChain()
}

fun String.isOutRange(range: Int) : Boolean {
    return length > range
}

/**
 * 判断字符串是否为整数
 * @return 是否为整数
 */
fun String.isNumeric(): Boolean {
    return matches("[-+]?\\d*\\.?\\d+".toRegex()) && !this.contains(".")
}

fun String.limitStringSize(size: Int): String {
    return if (length <= size) this else substring(0, size) + "..."
}

/**
 * 来自 Mirai 的 [asHumanReadable]
 */
@ExperimentalTime
fun kotlin.time.Duration.toFriendly(maxUnit: DurationUnit = TimeUnit.SECONDS): String {
    val days = toInt(DurationUnit.DAYS)
    val hours = toInt(DurationUnit.HOURS) % 24
    val minutes = toInt(DurationUnit.MINUTES) % 60
    val seconds = (toInt(DurationUnit.SECONDS) % 60 * 1000) / 1000
    val ms = (toInt(DurationUnit.MILLISECONDS) % 60 * 1000 * 1000) / 1000 / 1000
    return buildString {
        if (days != 0 && maxUnit >= TimeUnit.DAYS) append("${days}天")
        if (hours != 0 && maxUnit >= TimeUnit.HOURS) append("${hours}时")
        if (minutes != 0 && maxUnit >= TimeUnit.MINUTES) append("${minutes}分")
        if (seconds != 0 && maxUnit >= TimeUnit.SECONDS) append("${seconds}秒")
        append("${ms}毫秒")
    }
}

fun HttpResponse.isType(typeName: String): Boolean {
    val contentType = this.header("content-type") ?: return true
    return contentType.contains(typeName)
}

suspend fun MessageEvent.replyWithNullCheck(text: String?) {
    if (!text.isNullOrEmpty()) reply(text)
}

suspend fun MessageEvent.replyWithNullCheck(plainText: PlainText?) {
    if (plainText != null && plainText.isContentNotEmpty()) reply(plainText)
}

/**
 * 用于辅助机器人运行的各种工具方法
 *
 * @author Nameless
 */

object BotUtil {
    /**
     * 判断是否签到过了
     *
     * @author NamelessSAMA
     * @param user 机器人账号
     * @return 是否签到
     */
    fun isChecked(user: BotUser): Boolean {
        val now = LocalDateTime.now()
        val period = user.lastCheckInTime.toLocalDate().until(now.toLocalDate())

        return period.days == 0
    }

    /**
     * 判断指定QQ号是否仍在冷却中
     *
     * @author NamelessSAMA
     * @param qq 指定的QQ号
     * @return 目标QQ号是否处于冷却状态
     */
    fun isNoCoolDown(qq: Long): Boolean {
        if (cfg.coolDownTime < 1) return true

        val currentTime = System.currentTimeMillis()
        if (qq == 80000000L) {
            return false
        }

        if (qq == cfg.ownerId) {
            return true
        }

        if (coolDown.containsKey(qq) && !isBotAdmin(qq)) {
            val cd = coolDown[qq]
            if (cd != null) {
                if (currentTime - cd < cfg.coolDownTime * 1000) {
                    return false
                } else {
                    coolDown.remove(qq)
                }
            }
        } else {
            coolDown[qq] = currentTime
        }
        return true
    }

    /**
     * 判断指定QQ号是否仍在冷却中
     * (可以自定义命令冷却时间)
     *
     * @author Nameless
     * @param qq 要检测的QQ号
     * @param seconds 自定义冷却时间
     * @return 目标QQ号是否处于冷却状态
     */
    fun isNoCoolDown(qq: Long, seconds: Int): Boolean {
        if (seconds < 1) return true

        val currentTime = System.currentTimeMillis()
        if (qq == 80000000L) {
            return false
        }

        if (qq == cfg.ownerId) {
            return true
        }

        if (coolDown.containsKey(qq) && !isBotOwner(qq)) {
            if (currentTime - coolDown[qq]!! < seconds * 1000) {
                return false
            } else {
                coolDown.remove(qq)
            }
        } else {
            coolDown[qq] = currentTime
        }
        return true
    }

    /**
     * 判断ID是否符合育碧账号昵称格式规范
     *
     * @author NamelessSAMA
     * @param username 用户名
     * @return 是否符合规范
     */
    fun isLegitId(username: String): Boolean {
        return username.matches(Regex("[a-zA-Z0-9_.-]*"))
    }

    /**
     * 获取本地化文本
     *
     * @author NamelessSAMA
     * @param node 本地化文本节点
     * @return 本地化文本
     */
    fun getLocalMessage(node: String): String {
        for ((n, t) in BotVariables.localMessage) {
            if (n.contentEquals(node)) {
                return t
            }
        }
        return "PlaceHolder"
    }

    fun sendMessageToString(otherText: String?, addPrefix: Boolean = true): String {
        if (otherText.isNullOrEmpty()) return ""
        val sb = StringBuilder()
        if (addPrefix) sb.append(getLocalMessage("msg.bot-prefix")).append(" ")
        sb.append(otherText)
        return sb.toString().trim()
    }

    fun sendMessage(otherText: String?, addPrefix: Boolean = true): MessageChain {
        if (otherText.isNullOrEmpty()) return EmptyMessageChain
        val sb = StringBuilder()
        if (addPrefix) sb.append(getLocalMessage("msg.bot-prefix")).append(" ")
        sb.append(otherText)
        return sb.toString().trim().convertToChain()
    }

    fun sendMessage(vararg otherText: String?, addPrefix: Boolean): MessageChain {
        if (!otherText.isNullOrEmpty()) return "".convertToChain()

        val sb = StringBuilder()
        if (addPrefix) sb.append(getLocalMessage("msg.bot-prefix")).append(" ")
        otherText.forEach {
            sb.append(it).append("\n")
        }
        return sb.toString().trim().convertToChain()
    }

    /**
     * 获取用户的权限组等级
     *
     * @author NamelessSAMA
     * @param qq 指定用户的QQ号
     * @return 权限组等级
     */
    fun getLevel(qq: Long): UserLevel {
        val user = BotUser.getUser(qq)
        if (user != null) {
            return user.level
        }
        return UserLevel.USER
    }

    fun List<String>.getRestString(startAt: Int): String {
        val sb = StringBuilder()
        if (this.size == 1) {
            return this[0]
        }

        for (index in startAt until this.size) {
            sb.append(this[index]).append(" ")
        }
        return sb.toString().trim()
    }

    @ExperimentalTime
    fun getRunningTime(): String {
        val remain = Duration.between(BotVariables.startTime, LocalDateTime.now())
        return remain.toKotlinDuration().toFriendly(TimeUnit.DAYS)
    }

    fun getAt(event: MessageEvent, id: String) : BotUser? {
        val at = event.message[At]

        return if (at != null) {
            BotUser.getUser(at.target)
        } else {
            if (StringUtils.isNumeric(id)) {
                BotUser.getUser(id.toLong())
            } else {
                null
            }
        }
    }

    @ExperimentalTime
    fun getMemoryUsage(): String =
        "OS 信息: ${getOsInfo()}\n" +
                "JVM 版本: ${getJVMVersion()}\n" +
                "内存占用: ${getUsedMemory()}MB/${getMaxMemory()}MB\n" +
                "运行时长: ${getRunningTime()}"

    fun returnMsgIf(condition: Boolean, msg: MessageChain): MessageChain = if (condition) msg else EmptyMessageChain

    fun returnMsgIfElse(condition: Boolean, msg: MessageChain, default: MessageChain): MessageChain {
        if (condition) {
            return msg
        }
        return default
    }

    fun isValidJson(json: String): Boolean {
        val jsonElement: JsonElement = try {
            JsonParser.parseString(json)
        } catch (e: Exception) {
            return false
        }
        return jsonElement.isJsonObject
    }

    fun isValidJson(element: JsonElement): Boolean {
        return element.isJsonObject || element.isJsonArray
    }

    fun executeWithRetry(task: () -> Unit, retryTime: Int): Throwable? {
        if (retryTime >= 5) return ReachRetryLimitException()

        var initRetryTime = 0
        fun runTask(): Throwable? {
            try {
                if (initRetryTime <= retryTime) {
                    task()
                    return null
                }
            } catch (t: Throwable) {
                if (NetUtil.printIfTimeout(t, "Retried failed, ${t.message}")) {
                    initRetryTime++
                    runTask()
                } else {
                    if (t !is RateLimitException) return t
                }
            }

            return null
        }

        runTask()

        return null
    }
}
