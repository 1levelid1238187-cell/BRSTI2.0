package com.example.ai

import com.google.firebase.ai.type.FunctionDeclaration
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.Tool

object BrstiTools {
    fun buildAllTools(): List<Tool> {
        val declarations = listOf(
            // Application Control
            FunctionDeclaration(
                name = "openApp",
                description = "Opens an installed Android app by app name or package name.",
                parameters = mapOf(
                    "appNameOrPackage" to Schema.string("The app name (e.g. Spotify, Chrome) or package name (e.g. com.whatsapp)")
                )
            ),
            FunctionDeclaration(
                name = "closeSupportedApp",
                description = "Explains Android background app-closing limits and returns to Home screen or opens App Info so the owner can close an app.",
                parameters = mapOf(
                    "appName" to Schema.string("Optional app name the user asked to close")
                ),
                optionalParameters = listOf("appName")
            ),
            FunctionDeclaration(
                name = "findInstalledApps",
                description = "Searches installed launchable apps on the owner's phone matching a query.",
                parameters = mapOf(
                    "query" to Schema.string("Search keyword for app name or package, or empty string to list top apps")
                ),
                optionalParameters = listOf("query")
            ),
            FunctionDeclaration(
                name = "openYouTube",
                description = "Opens the YouTube app, optionally searching for a video or topic.",
                parameters = mapOf(
                    "searchQuery" to Schema.string("Optional search query to open inside YouTube")
                ),
                optionalParameters = listOf("searchQuery")
            ),
            FunctionDeclaration(
                name = "openWhatsApp",
                description = "Opens the WhatsApp application.",
                parameters = emptyMap()
            ),
            FunctionDeclaration(
                name = "openInstagram",
                description = "Opens the Instagram application.",
                parameters = emptyMap()
            ),
            FunctionDeclaration(
                name = "openFacebook",
                description = "Opens the Facebook application.",
                parameters = emptyMap()
            ),
            FunctionDeclaration(
                name = "openCamera",
                description = "Opens the Android device Camera app.",
                parameters = emptyMap()
            ),
            FunctionDeclaration(
                name = "openGallery",
                description = "Opens the phone's Gallery / Photos viewer or system Photo Picker.",
                parameters = emptyMap()
            ),
            FunctionDeclaration(
                name = "openSettings",
                description = "Opens Android System Settings or a specific settings section (wifi, bluetooth, battery, notifications, app).",
                parameters = mapOf(
                    "section" to Schema.string("Optional section: general, wifi, bluetooth, battery, notifications, permissions")
                ),
                optionalParameters = listOf("section")
            ),
            FunctionDeclaration(
                name = "openCalculator",
                description = "Opens the Android Calculator application.",
                parameters = emptyMap()
            ),

            // Phone & Calling
            FunctionDeclaration(
                name = "searchContact",
                description = "Searches the owner's phone contacts by name and returns matching contact names and phone numbers (requires READ_CONTACTS permission).",
                parameters = mapOf(
                    "contactName" to Schema.string("Name of the person to search in contacts")
                )
            ),
            FunctionDeclaration(
                name = "makePhoneCall",
                description = "Stages a phone call confirmation card for the owner before placing any call. Never places a call without confirmation.",
                parameters = mapOf(
                    "contactName" to Schema.string("Contact name or phone number to call")
                )
            ),
            FunctionDeclaration(
                name = "getCallHistory",
                description = "Checks whether call log access is permitted on Android and explains privacy/permission status or opens the Phone dialer recents.",
                parameters = mapOf(
                    "limit" to Schema.integer("Maximum number of call entries to summarize")
                ),
                optionalParameters = listOf("limit")
            ),

            // WhatsApp
            FunctionDeclaration(
                name = "findWhatsAppContact",
                description = "Finds a contact's phone number for WhatsApp messaging.",
                parameters = mapOf(
                    "contactName" to Schema.string("Name of the contact")
                )
            ),
            FunctionDeclaration(
                name = "prepareWhatsAppMessage",
                description = "Prepares a WhatsApp message preview and requires explicit owner confirmation before opening WhatsApp to send.",
                parameters = mapOf(
                    "contactName" to Schema.string("Recipient contact name or phone number"),
                    "message" to Schema.string("Message text to prepare")
                )
            ),
            FunctionDeclaration(
                name = "openWhatsAppChat",
                description = "Stages confirmation to open a WhatsApp chat with a contact.",
                parameters = mapOf(
                    "contactName" to Schema.string("Contact name or phone number")
                )
            ),
            FunctionDeclaration(
                name = "sendWhatsAppMessage",
                description = "Shows the prepared WhatsApp message and requires explicit owner confirmation before sending.",
                parameters = mapOf(
                    "contactName" to Schema.string("Recipient contact name or phone number"),
                    "message" to Schema.string("Message text to send")
                )
            ),

            // SMS
            FunctionDeclaration(
                name = "prepareSMS",
                description = "Prepares an SMS message preview and asks for explicit owner confirmation before opening the SMS app.",
                parameters = mapOf(
                    "contactName" to Schema.string("Recipient name or phone number"),
                    "message" to Schema.string("SMS text body")
                )
            ),
            FunctionDeclaration(
                name = "openSMSApp",
                description = "Stages confirmation to open the SMS composer with pre-filled recipient and message.",
                parameters = mapOf(
                    "contactName" to Schema.string("Recipient name or phone number"),
                    "message" to Schema.string("SMS text body")
                )
            ),

            // Gmail
            FunctionDeclaration(
                name = "prepareGmail",
                description = "Prepares a Gmail email draft (recipient, subject, body) and asks for explicit owner confirmation before composing.",
                parameters = mapOf(
                    "recipientEmail" to Schema.string("Recipient email address"),
                    "subject" to Schema.string("Email subject line"),
                    "body" to Schema.string("Email message body")
                )
            ),
            FunctionDeclaration(
                name = "openGmailCompose",
                description = "Stages confirmation to open Gmail compose with pre-filled recipient, subject, and body.",
                parameters = mapOf(
                    "recipientEmail" to Schema.string("Recipient email address"),
                    "subject" to Schema.string("Email subject line"),
                    "body" to Schema.string("Email message body")
                )
            ),

            // Phone Information
            FunctionDeclaration(
                name = "getBatteryLevel",
                description = "Reads the phone's current battery percentage and charging status.",
                parameters = emptyMap()
            ),
            FunctionDeclaration(
                name = "getDeviceModel",
                description = "Reads the Android device manufacturer, model name, and Android OS version.",
                parameters = emptyMap()
            ),
            FunctionDeclaration(
                name = "getCurrentDateTime",
                description = "Gets the current local date, day of week, and time on the phone.",
                parameters = emptyMap()
            ),
            FunctionDeclaration(
                name = "getInternetStatus",
                description = "Checks whether the phone currently has an active validated internet connection and its transport type.",
                parameters = emptyMap()
            ),
            FunctionDeclaration(
                name = "getWiFiStatus",
                description = "Checks whether Wi-Fi is enabled and currently connected.",
                parameters = emptyMap()
            ),
            FunctionDeclaration(
                name = "getInstalledApps",
                description = "Lists installed launchable apps on the device.",
                parameters = emptyMap()
            ),

            // Personal Assistant & Media
            FunctionDeclaration(
                name = "setAlarm",
                description = "Sets an alarm on the Android Clock app at a given 24-hour hour and minute with an optional label.",
                parameters = mapOf(
                    "hour" to Schema.integer("Hour in 24-hour format (0-23)"),
                    "minute" to Schema.integer("Minute (0-59)"),
                    "label" to Schema.string("Optional alarm label")
                ),
                optionalParameters = listOf("label")
            ),
            FunctionDeclaration(
                name = "createReminder",
                description = "Creates a local reminder for the owner in BRSTI's reminder list.",
                parameters = mapOf(
                    "title" to Schema.string("What to remind the owner about"),
                    "scheduleText" to Schema.string("When the reminder is for (e.g., Today at 6 PM)")
                )
            ),
            FunctionDeclaration(
                name = "getWeather",
                description = "Fetches real-time live weather for a city using Open-Meteo.",
                parameters = mapOf(
                    "city" to Schema.string("City name, e.g. Dhaka, Kolkata, London")
                )
            ),
            FunctionDeclaration(
                name = "searchInternet",
                description = "Searches the internet for real-time factual summaries and web links.",
                parameters = mapOf(
                    "query" to Schema.string("Search query")
                )
            ),
            FunctionDeclaration(
                name = "calculateMath",
                description = "Evaluates a mathematical expression accurately (arithmetic, powers, sqrt, sin, cos, tan, log).",
                parameters = mapOf(
                    "expression" to Schema.string("Mathematical expression, e.g. (25 * 4) + sqrt(144)")
                )
            ),
            FunctionDeclaration(
                name = "readNotifications",
                description = "Reads recent active device notifications ONLY if the owner has enabled Notification Access for BRSTI AI.",
                parameters = emptyMap()
            ),
            FunctionDeclaration(
                name = "saveUserPreference",
                description = "Saves a conversational preference or fact about the owner locally ONLY when memory consent is enabled or the owner asks to remember it.",
                parameters = mapOf(
                    "key" to Schema.string("Short preference key, e.g. favorite_drink, nickname, mood_note"),
                    "value" to Schema.string("Preference value to remember")
                )
            ),
            FunctionDeclaration(
                name = "getUserPreferences",
                description = "Retrieves saved conversational preferences remembered for the owner.",
                parameters = emptyMap()
            ),
            FunctionDeclaration(
                name = "requestPhotoCapture",
                description = "Asks the owner for explicit approval to capture a photo using the camera for Gemini analysis.",
                parameters = mapOf(
                    "reason" to Schema.string("Short explanation of why the photo is being captured")
                )
            ),
            FunctionDeclaration(
                name = "openPhotoPicker",
                description = "Opens the Android Photo Picker so the owner can select an image or video for BRSTI to analyze.",
                parameters = emptyMap()
            ),
            FunctionDeclaration(
                name = "enterSleepMode",
                description = "Puts BRSTI into sleep / wake-word standby mode when the owner says goodbye or asks her to sleep.",
                parameters = emptyMap()
            )
        )
        return listOf(Tool.functionDeclarations(declarations))
    }
}
