package com.example.device

import java.util.UUID

sealed interface PendingActionConfirmation {
    val id: String
    val title: String
    val summary: String

    data class PhoneCall(
        override val id: String = UUID.randomUUID().toString(),
        val contactName: String,
        val phoneNumber: String
    ) : PendingActionConfirmation {
        override val title: String = "Confirm Phone Call"
        override val summary: String = "Call $contactName ($phoneNumber)"
    }

    data class WhatsAppMessage(
        override val id: String = UUID.randomUUID().toString(),
        val contactName: String,
        val phoneNumber: String?,
        val message: String
    ) : PendingActionConfirmation {
        override val title: String = "Confirm WhatsApp Message"
        override val summary: String = "To: $contactName${phoneNumber?.let { " ($it)" } ?: ""}\n\"$message\""
    }

    data class SmsMessage(
        override val id: String = UUID.randomUUID().toString(),
        val contactName: String,
        val phoneNumber: String?,
        val message: String
    ) : PendingActionConfirmation {
        override val title: String = "Confirm SMS Message"
        override val summary: String = "To: $contactName${phoneNumber?.let { " ($it)" } ?: ""}\n\"$message\""
    }

    data class GmailDraft(
        override val id: String = UUID.randomUUID().toString(),
        val recipientEmail: String,
        val subject: String,
        val body: String
    ) : PendingActionConfirmation {
        override val title: String = "Confirm Gmail Compose"
        override val summary: String = "To: $recipientEmail\nSubject: $subject\n\n$body"
    }

    data class PhotoCapture(
        override val id: String = UUID.randomUUID().toString(),
        val reason: String
    ) : PendingActionConfirmation {
        override val title: String = "Confirm Camera Capture"
        override val summary: String = reason
    }

    data class OpenWebUrl(
        override val id: String = UUID.randomUUID().toString(),
        val label: String,
        val url: String
    ) : PendingActionConfirmation {
        override val title: String = "Open Web Search"
        override val summary: String = "$label\n$url"
    }
}
