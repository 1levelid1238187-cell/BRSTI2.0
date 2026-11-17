package com.example.device

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import com.example.data.local.MemoryAndReminderRepository
import com.example.data.local.SettingsRepository
import com.example.data.network.OnlineToolsClient
import com.example.permissions.PermissionManager
import com.example.service.BrstiNotificationListenerService
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ContactMatch(val name: String, val phoneNumber: String)

interface AssistantActionHost {
    fun stageConfirmation(confirmation: PendingActionConfirmation)
    fun openMediaVisionPicker()
    fun requestSleepMode()
    fun requestRuntimePermissionHint(permissionName: String)
}

class DeviceActionExecutor(
    private val context: Context,
    private val repository: MemoryAndReminderRepository,
    private val settingsRepository: SettingsRepository,
    private val onlineToolsClient: OnlineToolsClient
) {
    var actionHost: AssistantActionHost? = null

    suspend fun execute(functionName: String, args: Map<String, kotlinx.serialization.json.JsonElement>): JsonObject {
        return when (functionName) {
            "openApp" -> openApp(argString(args, "appNameOrPackage"))
            "closeSupportedApp" -> closeSupportedApp(argString(args, "appName"))
            "findInstalledApps" -> findInstalledApps(argString(args, "query"))
            "openYouTube" -> openYouTube(argString(args, "searchQuery"))
            "openWhatsApp" -> openPackage("com.whatsapp", "WhatsApp")
            "openInstagram" -> openPackage("com.instagram.android", "Instagram")
            "openFacebook" -> openPackage("com.facebook.katana", "Facebook")
            "openCamera" -> openCamera()
            "openGallery" -> openGallery()
            "openSettings" -> openSettings(argString(args, "section"))
            "openCalculator" -> openCalculator()
            "searchContact" -> searchContactTool(argString(args, "contactName"))
            "makePhoneCall" -> stagePhoneCall(argString(args, "contactName"))
            "getCallHistory" -> getCallHistoryInfo()
            "findWhatsAppContact" -> searchContactTool(argString(args, "contactName"))
            "prepareWhatsAppMessage", "sendWhatsAppMessage" -> stageWhatsAppMessage(
                argString(args, "contactName"),
                argString(args, "message")
            )
            "openWhatsAppChat" -> stageWhatsAppMessage(argString(args, "contactName"), "")
            "prepareSMS", "openSMSApp" -> stageSms(
                argString(args, "contactName"),
                argString(args, "message")
            )
            "prepareGmail", "openGmailCompose" -> stageGmail(
                argString(args, "recipientEmail"),
                argString(args, "subject"),
                argString(args, "body")
            )
            "getBatteryLevel" -> getBatteryLevel()
            "getDeviceModel" -> getDeviceModel()
            "getCurrentDateTime" -> getCurrentDateTime()
            "getInternetStatus" -> getInternetStatus()
            "getWiFiStatus" -> getWiFiStatus()
            "getInstalledApps" -> findInstalledApps("")
            "setAlarm" -> setAlarm(
                args["hour"]?.jsonPrimitive?.intOrNull ?: 7,
                args["minute"]?.jsonPrimitive?.intOrNull ?: 0,
                argString(args, "label")
            )
            "createReminder" -> createReminder(
                argString(args, "title"),
                argString(args, "scheduleText")
            )
            "getWeather" -> getWeather(argString(args, "city"))
            "searchInternet" -> searchInternet(argString(args, "query"))
            "calculateMath" -> calculateMath(argString(args, "expression"))
            "readNotifications" -> readNotifications()
            "saveUserPreference" -> saveUserPreference(
                argString(args, "key"),
                argString(args, "value")
            )
            "getUserPreferences" -> getUserPreferences()
            "requestPhotoCapture" -> {
                val reason = argString(args, "reason").ifBlank { "Capture a photo for BRSTI to analyze." }
                actionHost?.stageConfirmation(PendingActionConfirmation.PhotoCapture(reason = reason))
                okJson("Camera capture approval card shown to owner. Waiting for owner to tap Confirm.")
            }
            "openPhotoPicker" -> {
                actionHost?.openMediaVisionPicker()
                okJson("Opened Photo Picker screen for selecting an image or video.")
            }
            "enterSleepMode" -> {
                actionHost?.requestSleepMode()
                okJson("Entering sleep / wake-word standby mode.")
            }
            else -> errJson("Unsupported tool: $functionName")
        }
    }

    fun executeConfirmedAction(confirmation: PendingActionConfirmation): String {
        return when (confirmation) {
            is PendingActionConfirmation.PhoneCall -> {
                val cleanNum = Uri.encode(confirmation.phoneNumber)
                val useCallIntent = PermissionManager.hasPermission(context, Manifest.permission.CALL_PHONE)
                val action = if (useCallIntent) Intent.ACTION_CALL else Intent.ACTION_DIAL
                val intent = Intent(action, Uri.parse("tel:$cleanNum")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                launchIntentSafe(intent, if (useCallIntent) "Calling ${confirmation.contactName}" else "Opened dialer for ${confirmation.contactName}")
            }
            is PendingActionConfirmation.WhatsAppMessage -> {
                val digits = confirmation.phoneNumber?.filter { it.isDigit() || it == '+' }.orEmpty()
                val encodedMsg = URLEncoder.encode(confirmation.message, "UTF-8")
                val url = if (digits.isNotBlank()) {
                    "https://api.whatsapp.com/send?phone=${digits.removePrefix("+")}&text=$encodedMsg"
                } else {
                    "https://api.whatsapp.com/send?text=$encodedMsg"
                }
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (isPackageInstalled("com.whatsapp")) setPackage("com.whatsapp")
                }
                launchIntentSafe(intent, "Opened WhatsApp for ${confirmation.contactName}")
            }
            is PendingActionConfirmation.SmsMessage -> {
                val target = confirmation.phoneNumber.orEmpty()
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$target")).apply {
                    putExtra("sms_body", confirmation.message)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                launchIntentSafe(intent, "Opened SMS app for ${confirmation.contactName}")
            }
            is PendingActionConfirmation.GmailDraft -> {
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${Uri.encode(confirmation.recipientEmail)}")).apply {
                    putExtra(Intent.EXTRA_EMAIL, arrayOf(confirmation.recipientEmail))
                    putExtra(Intent.EXTRA_SUBJECT, confirmation.subject)
                    putExtra(Intent.EXTRA_TEXT, confirmation.body)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (isPackageInstalled("com.google.android.gm")) setPackage("com.google.android.gm")
                }
                launchIntentSafe(intent, "Opened email composer for ${confirmation.recipientEmail}")
            }
            is PendingActionConfirmation.OpenWebUrl -> {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(confirmation.url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                launchIntentSafe(intent, "Opened browser for ${confirmation.label}")
            }
            is PendingActionConfirmation.PhotoCapture -> {
                actionHost?.openMediaVisionPicker()
                "Opened camera & vision screen"
            }
        }
    }

    private fun openApp(appNameOrPackage: String): JsonObject {
        val query = appNameOrPackage.trim()
        if (query.isEmpty()) return errJson("Please provide an app name or package name.")

        context.packageManager.getLaunchIntentForPackage(query)?.let { intent ->
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return okJson("Opened $query.")
        }

        val resolved = queryLauncherApps().firstOrNull { info ->
            val label = info.loadLabel(context.packageManager).toString()
            label.contains(query, ignoreCase = true) || info.activityInfo.packageName.contains(query, ignoreCase = true)
        }
        return if (resolved != null) {
            val pkg = resolved.activityInfo.packageName
            val label = resolved.loadLabel(context.packageManager).toString()
            val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                okJson("Opened $label ($pkg).")
            } else {
                errJson("$label does not expose a launchable screen.")
            }
        } else {
            errJson("Could not find an installed app matching '$query'.")
        }
    }

    private fun closeSupportedApp(appName: String): JsonObject {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(homeIntent) }
        val targetInfo = if (appName.isNotBlank()) " '$appName'" else ""
        return okJson(
            "Android security policies do not allow assistant apps to force-stop other apps$targetInfo directly. " +
                "Returned to Home screen; you can swipe it away from Recents or force-stop in App Info."
        )
    }

    private fun findInstalledApps(query: String): JsonObject {
        val apps = queryLauncherApps()
            .map { info ->
                val label = info.loadLabel(context.packageManager).toString()
                val pkg = info.activityInfo.packageName
                label to pkg
            }
            .distinctBy { it.second }
            .filter { (label, pkg) ->
                query.isBlank() || label.contains(query, ignoreCase = true) || pkg.contains(query, ignoreCase = true)
            }
            .sortedBy { it.first.lowercase() }
            .take(25)

        val listJson = JsonArray(apps.map { (label, pkg) ->
            buildJsonObject {
                put("name", label)
                put("package", pkg)
            }
        })
        return buildJsonObject {
            put("success", true)
            put("count", apps.size)
            put("apps", listJson)
        }
    }

    private fun openYouTube(searchQuery: String): JsonObject {
        val intent = if (searchQuery.isNotBlank()) {
            val encoded = URLEncoder.encode(searchQuery, "UTF-8")
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$encoded")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (isPackageInstalled("com.google.android.youtube")) setPackage("com.google.android.youtube")
            }
        } else {
            context.packageManager.getLaunchIntentForPackage("com.google.android.youtube")
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://m.youtube.com")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return launchToolIntent(intent, "Opened YouTube.")
    }

    private fun openPackage(pkg: String, displayName: String): JsonObject {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
        return if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            launchToolIntent(intent, "Opened $displayName.")
        } else {
            errJson("$displayName is not installed on this device.")
        }
    }

    private fun openCamera(): JsonObject {
        val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val fallback = Intent(MediaStore.ACTION_IMAGE_CAPTURE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (intent.resolveActivity(context.packageManager) != null) {
            launchToolIntent(intent, "Opened Camera.")
        } else if (fallback.resolveActivity(context.packageManager) != null) {
            launchToolIntent(fallback, "Opened Camera.")
        } else {
            errJson("No camera app available on this device.")
        }
    }

    private fun openGallery(): JsonObject {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            type = "image/*"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (intent.resolveActivity(context.packageManager) != null) {
            launchToolIntent(intent, "Opened Gallery.")
        } else {
            actionHost?.openMediaVisionPicker()
            okJson("Opened Photo Picker.")
        }
    }

    private fun openSettings(section: String): JsonObject {
        val action = when (section.lowercase()) {
            "wifi", "wi-fi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "battery" -> Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
            "notifications" -> Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
            "permissions", "app" -> Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        val intent = if (action == Settings.ACTION_APPLICATION_DETAILS_SETTINGS) {
            Intent(action, Uri.fromParts("package", context.packageName, null))
        } else {
            Intent(action)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return launchToolIntent(intent, "Opened Android settings ($section).")
    }

    private fun openCalculator(): JsonObject {
        val catIntent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (catIntent.resolveActivity(context.packageManager) != null) {
            return launchToolIntent(catIntent, "Opened Calculator.")
        }
        val pkgIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.calculator")
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (pkgIntent != null) {
            launchToolIntent(pkgIntent, "Opened Calculator.")
        } else {
            errJson("Calculator app not found on this device. I can still calculate math expressions directly!")
        }
    }

    private fun searchContactTool(contactName: String): JsonObject {
        if (!PermissionManager.hasPermission(context, Manifest.permission.READ_CONTACTS)) {
            actionHost?.requestRuntimePermissionHint("READ_CONTACTS")
            return errJson("Contacts permission (READ_CONTACTS) is not granted yet. Please ask the owner to grant Contacts access in Permissions.")
        }
        val matches = findContacts(contactName)
        if (matches.isEmpty()) {
            return okJson("No contacts found matching '$contactName'.")
        }
        val arr = JsonArray(matches.map {
            buildJsonObject {
                put("name", it.name)
                put("phone", it.phoneNumber)
            }
        })
        return buildJsonObject {
            put("success", true)
            put("matches", arr)
        }
    }

    private fun stagePhoneCall(contactName: String): JsonObject {
        val looksLikeNumber = contactName.any { it.isDigit() } && contactName.count { it.isDigit() } >= 3
        val match = if (looksLikeNumber) {
            ContactMatch(contactName, contactName.filter { it.isDigit() || it == '+' })
        } else {
            if (!PermissionManager.hasPermission(context, Manifest.permission.READ_CONTACTS)) {
                actionHost?.requestRuntimePermissionHint("READ_CONTACTS")
                return errJson("Contacts permission is required to look up $contactName's number before confirming a call.")
            }
            findContacts(contactName).firstOrNull()
        }
        return if (match != null) {
            actionHost?.stageConfirmation(PendingActionConfirmation.PhoneCall(contactName = match.name, phoneNumber = match.phoneNumber))
            okJson("Call confirmation card displayed for ${match.name} (${match.phoneNumber}). Waiting for owner to tap Confirm.")
        } else {
            errJson("Could not find a phone number for '$contactName'.")
        }
    }

    private fun getCallHistoryInfo(): JsonObject {
        return okJson(
            "For privacy and Google Play policy compliance, BRSTI AI does not read raw call logs directly. " +
                "I can open the Phone app's recent calls screen or look up saved Contacts once permission is granted."
        )
    }

    private fun stageWhatsAppMessage(contactName: String, message: String): JsonObject {
        val resolved = resolveContactOptional(contactName)
        actionHost?.stageConfirmation(
            PendingActionConfirmation.WhatsAppMessage(
                contactName = resolved?.name ?: contactName,
                phoneNumber = resolved?.phoneNumber,
                message = message
            )
        )
        return okJson("Prepared WhatsApp message for ${resolved?.name ?: contactName}. Confirmation card is shown on screen—waiting for owner approval.")
    }

    private fun stageSms(contactName: String, message: String): JsonObject {
        val resolved = resolveContactOptional(contactName)
        actionHost?.stageConfirmation(
            PendingActionConfirmation.SmsMessage(
                contactName = resolved?.name ?: contactName,
                phoneNumber = resolved?.phoneNumber,
                message = message
            )
        )
        return okJson("Prepared SMS for ${resolved?.name ?: contactName}. Confirmation card is shown on screen—waiting for owner approval.")
    }

    private fun stageGmail(recipientEmail: String, subject: String, body: String): JsonObject {
        actionHost?.stageConfirmation(
            PendingActionConfirmation.GmailDraft(
                recipientEmail = recipientEmail,
                subject = subject,
                body = body
            )
        )
        return okJson("Prepared Gmail draft to $recipientEmail. Confirmation card is shown on screen—waiting for owner approval.")
    }

    private fun getBatteryLevel(): JsonObject {
        val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (level >= 0 && scale > 0) (level * 100) / scale else -1
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        return buildJsonObject {
            put("success", true)
            put("batteryPercent", pct)
            put("isCharging", isCharging)
        }
    }

    private fun getDeviceModel(): JsonObject {
        return buildJsonObject {
            put("success", true)
            put("manufacturer", Build.MANUFACTURER)
            put("model", Build.MODEL)
            put("androidVersion", Build.VERSION.RELEASE)
            put("sdkInt", Build.VERSION.SDK_INT)
        }
    }

    private fun getCurrentDateTime(): JsonObject {
        val now = Date()
        val timeFmt = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(now)
        val dateFmt = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(now)
        return buildJsonObject {
            put("success", true)
            put("time", timeFmt)
            put("date", dateFmt)
        }
    }

    private fun getInternetStatus(): JsonObject {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = cm?.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        val connected = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val transport = when {
            caps == null -> "None"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile Data"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Other"
        }
        return buildJsonObject {
            put("success", true)
            put("isConnected", connected)
            put("transport", transport)
        }
    }

    @SuppressLint("MissingPermission")
    private fun getWiFiStatus(): JsonObject {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val caps = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val wifiConnected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        return buildJsonObject {
            put("success", true)
            put("wifiEnabled", wifiManager?.isWifiEnabled ?: false)
            put("wifiConnected", wifiConnected)
        }
    }

    private fun setAlarm(hour: Int, minute: Int, label: String): JsonObject {
        val clampedHour = hour.coerceIn(0, 23)
        val clampedMin = minute.coerceIn(0, 59)
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, clampedHour)
            putExtra(AlarmClock.EXTRA_MINUTES, clampedMin)
            if (label.isNotBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return launchToolIntent(intent, "Alarm intent opened for %02d:%02d.".format(clampedHour, clampedMin))
    }

    private suspend fun createReminder(title: String, scheduleText: String): JsonObject {
        if (title.isBlank()) return errJson("Reminder title cannot be empty.")
        val id = repository.addReminder(title, scheduleText.ifBlank { "Anytime" })
        return buildJsonObject {
            put("success", true)
            put("id", id)
            put("message", "Saved reminder '$title' (${scheduleText.ifBlank { "Anytime" }}).")
        }
    }

    private suspend fun getWeather(city: String): JsonObject {
        val result = onlineToolsClient.fetchWeather(city)
        return result.fold(
            onSuccess = { report ->
                buildJsonObject {
                    put("success", true)
                    put("location", report.locationName)
                    put("temperatureCelsius", report.temperatureCelsius)
                    put("feelsLikeCelsius", report.apparentCelsius)
                    put("humidityPercent", report.humidityPercent)
                    put("windKmh", report.windKmh)
                    put("condition", report.conditionSummary)
                }
            },
            onFailure = { errJson(it.localizedMessage ?: "Failed to fetch live weather.") }
        )
    }

    private suspend fun searchInternet(query: String): JsonObject {
        if (query.isBlank()) return errJson("Search query cannot be empty.")
        val result = onlineToolsClient.searchInternet(query)
        return result.fold(
            onSuccess = { snippet ->
                buildJsonObject {
                    put("success", true)
                    put("title", snippet.title)
                    put("summary", snippet.summary)
                    put("url", snippet.sourceUrl)
                }
            },
            onFailure = { errJson(it.localizedMessage ?: "Internet search failed.") }
        )
    }

    private fun calculateMath(expression: String): JsonObject {
        return MathEvaluator.evaluate(expression).fold(
            onSuccess = { value ->
                val formatted = if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.6f", value).trimEnd('0').trimEnd('.')
                buildJsonObject {
                    put("success", true)
                    put("expression", expression)
                    put("result", formatted)
                }
            },
            onFailure = { errJson("Could not evaluate '$expression': ${it.localizedMessage}") }
        )
    }

    private fun readNotifications(): JsonObject {
        val snapshot = PermissionManager.captureSnapshot(context)
        if (!snapshot.notificationListenerEnabled) {
            return errJson("Notification Access is not enabled. Please enable BRSTI AI Notification Reader in Settings -> Notification Access.")
        }
        val items = BrstiNotificationListenerService.recentNotifications.value
        val arr = JsonArray(items.map { item ->
            buildJsonObject {
                put("app", item.appName)
                put("title", item.title)
                put("text", item.text)
            }
        })
        return buildJsonObject {
            put("success", true)
            put("count", items.size)
            put("notifications", arr)
        }
    }

    private suspend fun saveUserPreference(key: String, value: String): JsonObject {
        val settings = settingsRepository.getSnapshot()
        if (!settings.memoryConsentGranted) {
            return errJson("Memory consent is currently turned OFF in Settings. Ask the owner to enable Conversational Memory first.")
        }
        if (key.isBlank() || value.isBlank()) return errJson("Preference key and value are required.")
        repository.saveMemory(key, value)
        return okJson("Remembered '$key' = '$value'.")
    }

    private suspend fun getUserPreferences(): JsonObject {
        val settings = settingsRepository.getSnapshot()
        if (!settings.memoryConsentGranted) {
            return okJson("Conversational memory is disabled by owner preference.")
        }
        val items = repository.getMemoriesSnapshot()
        val arr = JsonArray(items.map {
            buildJsonObject {
                put("key", it.key)
                put("value", it.value)
            }
        })
        return buildJsonObject {
            put("success", true)
            put("memories", arr)
        }
    }

    private fun resolveContactOptional(contactName: String): ContactMatch? {
        if (contactName.any { it.isDigit() } && contactName.count { it.isDigit() } >= 5) {
            return ContactMatch(contactName, contactName.filter { it.isDigit() || it == '+' })
        }
        if (!PermissionManager.hasPermission(context, Manifest.permission.READ_CONTACTS)) return null
        return findContacts(contactName).firstOrNull()
    }

    private fun findContacts(query: String): List<ContactMatch> {
        val results = mutableListOf<ContactMatch>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%${query.trim()}%")
        runCatching {
            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext() && results.size < 8) {
                    val name = cursor.getString(nameIdx).orEmpty()
                    val num = cursor.getString(numIdx).orEmpty()
                    if (name.isNotBlank() && num.isNotBlank()) {
                        results.add(ContactMatch(name, num))
                    }
                }
            }
        }
        return results.distinctBy { it.phoneNumber }
    }

    private fun queryLauncherApps(): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentActivities(intent, 0)
        }
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        }.getOrDefault(false)
    }

    private fun launchToolIntent(intent: Intent, successMessage: String): JsonObject {
        return runCatching {
            context.startActivity(intent)
            okJson(successMessage)
        }.getOrElse { e ->
            errJson("Could not open target app: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    private fun launchIntentSafe(intent: Intent, successMessage: String): String {
        return runCatching {
            context.startActivity(intent)
            successMessage
        }.getOrElse { e ->
            "Action failed: ${e.localizedMessage ?: "No compatible app found"}"
        }
    }

    private fun argString(args: Map<String, kotlinx.serialization.json.JsonElement>, key: String): String {
        return args[key]?.jsonPrimitive?.contentOrNull.orEmpty()
    }

    private fun okJson(message: String): JsonObject = buildJsonObject {
        put("success", true)
        put("message", JsonPrimitive(message))
    }

    private fun errJson(message: String): JsonObject = buildJsonObject {
        put("success", false)
        put("error", JsonPrimitive(message))
    }
}
