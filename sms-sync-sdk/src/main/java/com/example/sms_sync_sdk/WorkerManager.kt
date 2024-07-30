package com.example.sms_sync_sdk

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.Settings
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.S3ClientOptions
import com.amazonaws.services.s3.model.PutObjectRequest
import com.google.gson.*
import java.io.File
import java.io.FileWriter
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

const val awsAccessKey: String = "";
const val awsSecretKey: String = "";
const val awsBucketName: String = "";
const val awsEndpoint: String = "";

class UserWriteSMS(appContext: Context, workerParams: WorkerParameters)
    : Worker(appContext, workerParams) {

    private fun initAmazonS3Client(
        endpoint: String,
        accessKey: String,
        secretKey: String
    ) =
        AmazonS3Client(
            BasicAWSCredentials(accessKey, secretKey)
        ).apply {
            setEndpoint(endpoint).apply {
                println("S3 endpoint is ${endpoint}")
            }
            setRegion(Region.getRegion(Regions.AP_SOUTH_1))

            setS3ClientOptions(
                S3ClientOptions.builder()
                    .setPathStyleAccess(true).build()
            )
        }

    private fun uploadToS3(file: File, fileName: String) {

        val s3Client = initAmazonS3Client(awsEndpoint, awsAccessKey, awsSecretKey);

        val request = PutObjectRequest(awsBucketName, "$fileName.json", file)

        s3Client.putObject(request);

    }


    override fun doWork(): Result {

        return try {

            val fileName = inputData.getString("SYNC_ID")

            val path = applicationContext.filesDir;

            val letDirectory = File(path, "sms2s3")

            if (!letDirectory.exists()) {
                letDirectory.mkdirs()
            }

            val fileExists = File("$fileName.json")

            if (!fileExists.exists()) {

                val smsFile = File(letDirectory, "$fileName.json")

                val inboxUri: Uri = Uri.parse("content://sms/inbox");

                val cursor: Cursor? =
                    applicationContext.contentResolver.query(inboxUri, null, null, null, null)

                val msgData = JsonArray()
                val gson = GsonBuilder().disableHtmlEscaping().serializeNulls().create()
//                val parser = JsonParser()
                val androidDeviceId = Settings.Secure.getString(
                    applicationContext.contentResolver,
                    Settings.Secure.ANDROID_ID
                )

                if (cursor!!.moveToFirst()) {
                    val dynamicSmsData = JsonObject();
                    do {
                        for (idx in 0 until cursor!!.columnCount) {
                            if (cursor!!.getColumnName(idx).toString() == "body"
                                || cursor!!.getColumnName(idx).toString() == "address"
                                || cursor!!.getColumnName(idx).toString() == "date") {

                                val columnName = cursor!!.getColumnName(idx)
                                val value = cursor.getString(idx)

                                when (columnName) {
                                    "body" -> dynamicSmsData.addProperty("body", value)
                                    "address" -> dynamicSmsData.addProperty("from", value)
                                    "date" -> {
                                        val milliseconds = value.toLong()
                                        val cvDate: LocalDateTime =
                                            Instant.ofEpochMilli(milliseconds)
                                                .atZone(ZoneId.systemDefault()).toLocalDateTime()
                                        dynamicSmsData.addProperty("time", cvDate.toString())
                                    }
                                    else -> {
                                        println("do nothing")
                                    }
                                }
                            }
                        }
                        dynamicSmsData.addProperty("deviceId", androidDeviceId)
                        dynamicSmsData.addProperty("cid", fileName)
                        msgData.add(gson.toJsonTree(dynamicSmsData))

                    } while (cursor!!.moveToNext())

                    FileWriter(smsFile, false)
                        .use {
                            it.write(gson.toJson(msgData))
                        }
                    uploadToS3(smsFile, "$fileName")
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
