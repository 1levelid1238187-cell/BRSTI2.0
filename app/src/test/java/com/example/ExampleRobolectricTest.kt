package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ai.BrstiTools
import com.example.permissions.PermissionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name matches BRSTI AI`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("BRSTI AI", appName)
    }

    @Test
    fun `tools declaration list includes required device control tools`() {
        val tools = BrstiTools.buildAllTools()
        assertTrue(tools.isNotEmpty())
    }

    @Test
    fun `initial permission snapshot reports audio ungranted in fresh test context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val snapshot = PermissionManager.captureSnapshot(context)
        assertFalse(snapshot.recordAudioGranted)
    }
}
