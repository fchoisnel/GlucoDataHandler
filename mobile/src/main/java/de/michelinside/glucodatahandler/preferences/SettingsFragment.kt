package de.michelinside.glucodatahandler.preferences

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.*
import de.michelinside.glucodatahandler.BuildConfig
import de.michelinside.glucodatahandler.R
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifyDataSource


class SettingsFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {
    private val LOG_ID = "GlucoDataHandler.SettingsFragment"
    private lateinit var activityResultOverlayLauncher: ActivityResultLauncher<Intent>

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        Log.d(LOG_ID, "onCreatePreferences called")
        try {
            preferenceManager.sharedPreferencesName = Constants.SHARED_PREF_TAG
            setPreferencesFromResource(R.xml.preferences, rootKey)

            if (BuildConfig.DEBUG) {
                val notifySwitch =
                    findPreference<SwitchPreferenceCompat>(Constants.SHARED_PREF_NOTIFICATION)
                notifySwitch!!.isVisible = true
            }

            val selectTargets = findPreference<MultiSelectListPreference>(Constants.SHARED_PREF_GLUCODATA_RECEIVERS)
            val receivers = getReceivers()
            // force "global broadcast" to be the first entry
            selectTargets!!.entries = arrayOf<CharSequence>(resources.getString(R.string.pref_global_broadcast)) + receivers.keys.toTypedArray()
            selectTargets.entryValues = arrayOf<CharSequence>("") + receivers.values.toTypedArray()

            activityResultOverlayLauncher = registerForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode != Activity.RESULT_OK) {
                    InternalNotifier.notify(requireContext(), NotifyDataSource.SETTINGS, null)
                }
                // Do next thing
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onCreatePreferences exception: " + exc.toString())
        }
    }

    override fun onResume() {
        Log.d(LOG_ID, "onResume called")
        try {
            preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
            updateEnableStates(preferenceManager.sharedPreferences!!)
            super.onResume()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onResume exception: " + exc.toString())
        }
    }

    override fun onPause() {
        Log.d(LOG_ID, "onPause called")
        try {
            preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
            super.onPause()
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onPause exception: " + exc.toString())
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        Log.d(LOG_ID, "onSharedPreferenceChanged called for " + key)
        try {
            when(key) {
                Constants.SHARED_PREF_PERMANENT_NOTIFICATION,
                Constants.SHARED_PREF_SECOND_PERMANENT_NOTIFICATION,
                Constants.SHARED_PREF_SEND_TO_GLUCODATA_AOD -> {
                    updateEnableStates(sharedPreferences!!)
                }
                Constants.SHARED_PREF_FLOATING_WIDGET -> {
                    updateEnableStates(sharedPreferences!!)
                    if (sharedPreferences.getBoolean(Constants.SHARED_PREF_FLOATING_WIDGET, false) && !Settings.canDrawOverlays(requireContext())) {
                        getPermission()
                    }
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onSharedPreferenceChanged exception: " + exc.toString())
        }
    }

    private fun getPermission() {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + requireContext().packageName)
            )
            activityResultOverlayLauncher.launch(intent)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "getPermission exception: " + exc.toString())
        }
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        Log.d(LOG_ID, "onDisplayPreferenceDialog called for " + preference.javaClass)
        try {
            if (preference is SelectReceiverPreference) {
                Log.d(LOG_ID, "Show SelectReceiver Dialog")
                val dialogFragment = SelectReceiverPreferenceDialogFragmentCompat.initial(preference.key)
                dialogFragment.setTargetFragment(this, 0)
                dialogFragment.show(parentFragmentManager, "androidx.preference.PreferenceFragment.DIALOG")
            } else {
                super.onDisplayPreferenceDialog(preference)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onDisplayPreferenceDialog exception: " + exc.toString())
        }
    }

    fun <T : Preference?> setEnableState(sharedPreferences: SharedPreferences, key: String, enableKey: String, secondEnableKey: String? = null, defValue: Boolean = false) {
        val pref = findPreference<T>(key)
        if (pref != null)
            pref.isEnabled = sharedPreferences.getBoolean(enableKey, defValue) && (if (secondEnableKey != null) sharedPreferences.getBoolean(secondEnableKey, defValue) else true)
    }

    fun updateEnableStates(sharedPreferences: SharedPreferences) {
        try {
            setEnableState<MultiSelectListPreference>(sharedPreferences, Constants.SHARED_PREF_GLUCODATA_RECEIVERS, Constants.SHARED_PREF_SEND_TO_GLUCODATA_AOD)
            setEnableState<ListPreference>(sharedPreferences, Constants.SHARED_PREF_PERMANENT_NOTIFICATION_ICON, Constants.SHARED_PREF_PERMANENT_NOTIFICATION)
            setEnableState<SwitchPreferenceCompat>(sharedPreferences, Constants.SHARED_PREF_PERMANENT_NOTIFICATION_EMPTY, Constants.SHARED_PREF_PERMANENT_NOTIFICATION)
            setEnableState<SwitchPreferenceCompat>(sharedPreferences, Constants.SHARED_PREF_PERMANENT_NOTIFICATION_USE_BIG_ICON, Constants.SHARED_PREF_PERMANENT_NOTIFICATION)
            setEnableState<SwitchPreferenceCompat>(sharedPreferences, Constants.SHARED_PREF_SECOND_PERMANENT_NOTIFICATION, Constants.SHARED_PREF_PERMANENT_NOTIFICATION)
            setEnableState<ListPreference>(sharedPreferences, Constants.SHARED_PREF_SECOND_PERMANENT_NOTIFICATION_ICON, Constants.SHARED_PREF_PERMANENT_NOTIFICATION, Constants.SHARED_PREF_SECOND_PERMANENT_NOTIFICATION)
            setEnableState<SeekBarPreference>(sharedPreferences, Constants.SHARED_PREF_FLOATING_WIDGET_SIZE, Constants.SHARED_PREF_FLOATING_WIDGET)
            setEnableState<ListPreference>(sharedPreferences, Constants.SHARED_PREF_FLOATING_WIDGET_STYLE, Constants.SHARED_PREF_FLOATING_WIDGET)
        } catch (exc: Exception) {
            Log.e(LOG_ID, "updateEnableStates exception: " + exc.toString())
        }
    }

    private fun getReceivers(): HashMap<String, String> {
        val names = HashMap<String, String>()
        try {
            val receivers: List<ResolveInfo>
            val intent = Intent(Constants.GLUCODATA_BROADCAST_ACTION)
            receivers = requireContext().packageManager.queryBroadcastReceivers(
                intent,
                PackageManager.GET_META_DATA
            )
            for (resolveInfo in receivers) {
                val pkgName = resolveInfo.activityInfo.packageName
                val name =
                    resolveInfo.activityInfo.loadLabel(requireContext().packageManager).toString()
                if (pkgName != null && pkgName != requireContext().packageName) {
                    names[name] = pkgName
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "getReceivers exception: " + exc.toString())
        }
        return names
    }
}