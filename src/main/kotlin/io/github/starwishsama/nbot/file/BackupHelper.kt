package io.github.starwishsama.nbot.file

import cn.hutool.core.io.file.FileWriter
import io.github.starwishsama.nbot.BotConstants
import io.github.starwishsama.nbot.BotInstance
import io.github.starwishsama.nbot.managers.TaskManager
import java.io.File
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

object BackupHelper {
    private val location: File = File(BotInstance.filePath.toString() + "/backups")

    private fun createBackup(){
        try {
            if (!location.exists()) {
                location.mkdirs()
            }

            val backupTime = LocalDateTime.now()
            val backupName =
                    "backup-${backupTime.year}-${backupTime.month.value}-${backupTime.dayOfMonth}-${backupTime.hour}-${backupTime.minute}.json"

            val backupFile = File(BotInstance.filePath.toString() + "/backups/${backupName}")
            backupFile.createNewFile()
            FileWriter.create(backupFile, Charsets.UTF_8)
                    .write(BotConstants.gson.toJson(BotConstants.users))
            BotInstance.logger.info("[备份] 备份成功! 文件名是${backupName}")
        } catch (e: Exception) {
            BotInstance.logger.error("[备份] 备份时出问题", e)
        }
    }

    fun scheduleBackup() {
        TaskManager.runScheduleTaskAsync(BackupHelper::createBackup, 0, 3, TimeUnit.HOURS)
    }
}