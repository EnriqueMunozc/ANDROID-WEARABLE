package com.example.focuswear.routine

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService

data class IncomingItem(val uri: Uri, val payload: String)

abstract class FocusDataListener : WearableListenerService() {
    final override fun onDataChanged(events: DataEventBuffer) {
        val items = events.filter { it.type == DataEvent.TYPE_CHANGED }
            .mapNotNull { it.dataItem.toIncoming() }
        if (items.isNotEmpty()) {
            Handler(Looper.getMainLooper()).post {
                items.forEach(::receive)
            }
        }
    }

    abstract fun receive(item: IncomingItem)
}

private fun DataItem.toIncoming(): IncomingItem? {
    val bytes = data ?: return null
    if (uri.host.isNullOrEmpty() || bytes.size !in 1..256) return null
    return IncomingItem(uri, bytes.toString(Charsets.UTF_8))
}

/** The local outbox survives process death until Play services accepts each operation. */
class DataSync private constructor(context: Context) {
    val preferences: SharedPreferences =
        context.getSharedPreferences("focuswear_sync", Context.MODE_PRIVATE)
    private val client = Wearable.getDataClient(context)
    private val jobs = context.getSystemService(JobScheduler::class.java)
    private val retryService = ComponentName(context, SyncRetryJob::class.java)
    private var flushing = false

    fun queuePut(edit: SharedPreferences.Editor, path: String, payload: String) {
        scheduleRetry()
        edit.putString(PUT + path, payload)
    }

    fun consume(uri: Uri) {
        scheduleRetry()
        if (preferences.edit().putString(DELETE + uri, "").commit()) kick()
    }

    fun read(prefix: String, receive: (IncomingItem) -> Unit) {
        client.getDataItems(Uri.parse("wear://*$prefix"), DataClient.FILTER_PREFIX)
            .addOnSuccessListener { items ->
                try {
                    items.mapNotNull { it.toIncoming() }.forEach(receive)
                } finally {
                    items.release()
                }
            }
            .addOnFailureListener { Log.w(TAG, "Could not read Data Layer items", it) }
    }

    fun kick() {
        if (pending().isEmpty()) return
        scheduleRetry()
        flush()
    }

    private fun scheduleRetry() {
        if (jobs.getPendingJob(JOB_ID) == null) {
            val status = jobs.schedule(
                JobInfo.Builder(JOB_ID, retryService)
                    .setPersisted(true)
                    .setMinimumLatency(15_000)
                    .setBackoffCriteria(30_000, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                    .build()
            )
            if (status == JobScheduler.RESULT_FAILURE) Log.w(TAG, "Could not schedule the Data Layer retry")
        }
    }

    private fun pending() = preferences.all.filterKeys { it.startsWith(PUT) || it.startsWith(DELETE) }

    internal fun flush(done: ((retry: Boolean) -> Unit)? = null) {
        if (flushing) {
            done?.invoke(true)
            return
        }
        val entries = pending()
        if (entries.isEmpty()) {
            done?.invoke(false)
            return
        }
        flushing = true
        val tasks = entries.map { (key, value) ->
            val task = if (key.startsWith(PUT)) {
                val request = PutDataRequest.create(key.removePrefix(PUT))
                    .setData((value as String).toByteArray())
                    .setUrgent()
                client.putDataItem(request)
            } else {
                client.deleteDataItems(Uri.parse(key.removePrefix(DELETE)))
            }
            task.continueWith { completed ->
                if (completed.isSuccessful && preferences.getString(key, null) == value) {
                    preferences.edit().remove(key).commit()
                }
                if (!completed.isSuccessful) {
                    Log.w(TAG, "Data Layer operation will be retried", completed.exception)
                }
                completed.isSuccessful
            }
        }
        Tasks.whenAllComplete(tasks).addOnCompleteListener {
            flushing = false
            val remaining = pending()
            done?.invoke(remaining.isNotEmpty())
            if (remaining.any { (key, value) -> entries[key] != value }) kick()
        }
    }

    companion object {
        private const val JOB_ID = 17531
        private const val TAG = "FocusWearSync"
        private const val PUT = "put:"
        private const val DELETE = "delete:"
        private var instance: DataSync? = null

        // Activities, receivers and listener dispatch all use the main looper.
        fun from(context: Context): DataSync =
            instance ?: DataSync(context.applicationContext).also { instance = it }
    }
}

class SyncRetryJob : JobService() {
    private var running: JobParameters? = null

    override fun onStartJob(params: JobParameters): Boolean {
        running = params
        // Post so that even an empty outbox completes after onStartJob returns.
        Handler(Looper.getMainLooper()).post {
            if (running === params) {
                DataSync.from(this).flush { retry ->
                    if (running === params) {
                        running = null
                        jobFinished(params, retry)
                    }
                }
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running = null
        return true
    }
}
