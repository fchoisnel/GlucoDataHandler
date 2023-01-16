package de.michelinside.glucodatahandler.common

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcel
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.Wearable
import kotlin.coroutines.cancellation.CancellationException


open class GlucoseDataReceiverBase : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            val action = intent.action
            if (action != ACTION) {
                Log.e(LOG_ID, "action=" + action + " != " + ACTION)
                return
            }

            if( ReceiveData.handleIntent(context, intent.extras) )
                notify(context, intent.extras)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "Receive exception: " + exc.message.toString() )
        }
    }

    fun bundleToBytes(bundle: Bundle?): ByteArray? {
        val parcel = Parcel.obtain()
        parcel.writeBundle(bundle)
        val bytes = parcel.marshall()
        parcel.recycle()
        return bytes
    }

    open fun notify(context: Context, extras: Bundle?)
    {
        Log.d(LOG_ID, "Forward received intent.extras")
        Thread(Runnable {
            SendMessage(context, bundleToBytes(extras))
        }).start()
    }

    companion object {
        private const val LOG_ID = "GlucoDataHandler.Receiver.Base"
        private const val ACTION = "glucodata.Minute"
    }

    fun SendMessage(context: Context, glucodataIntent: ByteArray?)
    {
        try {
            val capabilityInfo: CapabilityInfo = Tasks.await(
                Wearable.getCapabilityClient(context).getCapability(Constants.CAPABILITY, CapabilityClient.FILTER_REACHABLE))
            Log.d(LOG_ID, "nodes received")
            val nodes = capabilityInfo.nodes
            //val nodes = Tasks.await(Wearable.getNodeClient(context).connectedNodes)
            Log.d(LOG_ID, nodes.size.toString() + " nodes found")
            if( nodes.size > 0 ) {
                // Send a message to all nodes in parallel
                nodes.map { node ->
                    val sendTask: Task<*> = Wearable.getMessageClient(context).sendMessage(
                        node.id,
                        Constants.GLUCODATA_INTENT_MESSAGE_PATH,
                        glucodataIntent
                    ).apply {
                        addOnSuccessListener {
                            Log.d(
                                LOG_ID,
                                "Data send to node " + node.toString()
                            )
                        }
                        addOnFailureListener {
                            Log.e(
                                LOG_ID,
                                "Failed to send data to node " + node.toString()
                            )
                        }
                    }
                }
            }
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (exception: Exception) {
            Log.e(LOG_ID, "Sending message failed: $exception")
        }
    }
}