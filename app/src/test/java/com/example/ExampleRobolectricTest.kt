package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.model.VlessProfile
import com.example.data.repository.ServerRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Maximus", appName)
    }

    @Test
    fun `database insert and retrieve profile`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = AppDatabase.getInstance(context)
        val repository = ServerRepository(db.serverProfileDao())

        val testProfile = VlessProfile(
            name = "Test Robolectric Node",
            address = "127.0.0.1",
            port = 443,
            uuid = "11112222-3333-4444-5555-666677778888",
            transport = "tcp",
            security = "reality"
        )

        repository.insert(testProfile)
        val retrieved = repository.getProfileById(testProfile.id)

        assertNotNull(retrieved)
        assertEquals("Test Robolectric Node", retrieved?.name)
        assertEquals("127.0.0.1", retrieved?.address)
    }
}
