package com.example.sms_sync_sdk

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.work.*
import androidx.lifecycle.Observer


@Composable
fun StartSyncSms(syncId: String) {
    val ctx = LocalContext.current;
    val lifeCycleOwner = LocalLifecycleOwner.current

    val workerConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED) //checks whether device should have Network Connection
        .build()

    val yourWorkRequest = OneTimeWorkRequestBuilder<UserWriteSMS>()
        .setConstraints(workerConstraints)
            .setInputData(createInputData(syncId))
        .build()

    WorkManager.getInstance(ctx).enqueue(yourWorkRequest)

    WorkManager.getInstance(ctx).getWorkInfoByIdLiveData(yourWorkRequest.id)
        .observe(lifeCycleOwner, Observer { workInfo ->
            if (workInfo != null && workInfo.state == WorkInfo.State.SUCCEEDED) {
                println("The worker request has been completed successfully")
            } else if (workInfo != null && workInfo.state == WorkInfo.State.FAILED) {
                println("The worker request failed")
            }
        })
}

fun createInputData(syncId: String): Data {
    return Data.Builder()
        .putString("SYNC_ID", syncId)
        .build()
}

