package io.github.starwishsama.nbot.config

import cn.hutool.core.io.file.FileWriter
import com.google.gson.GsonBuilder
import io.github.starwishsama.nbot.BotConstants
import io.github.starwishsama.nbot.BotInstance
import java.io.File
import java.lang.Exception
import java.nio.charset.Charset
import java.time.LocalDateTime

object BackupHelper {
    private val location: File = File(BotInstance.filePath.toString() + "/backups")

    fun createBackup(){
        try {
            if (!location.exists()) {
                location.mkdirs()
            }

            val backupTime = LocalDateTime.now()
            val backupName =
                "backup-${backupTime.year}-${backupTime.month.value}-${backupTime.dayOfMonth}-${backupTime.hour}-${backupTime.minute}.json"

            val backupFile = File(BotInstance.filePath.toString() + "/backups/${backupName}")
            backupFile.createNewFile()
            FileWriter.create(backupFile, Charset.forName("UTF-8"))
                .write(GsonBuilder().serializeNulls().create().toJson(BotConstants.users))
            BotInstance.logger.info("[备份] 备份成功! 文件名是${backupName}")
        } catch (e: Exception){
            BotInstance.logger.error("[备份] 备份时出问题", e)
        }
    }
}