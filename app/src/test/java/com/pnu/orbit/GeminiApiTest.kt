package com.pnu.orbit

import org.junit.Test
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Properties

class GeminiApiTest {

    @Test
    fun testGeminiApi() {
        // 1. Try to read API key from local.properties
        var apiKey = ""
        val properties = Properties()
        val localPropertiesFile = File("../local.properties") // project root relative to app/ directory
        if (localPropertiesFile.exists()) {
            localPropertiesFile.inputStream().use { properties.load(it) }
            apiKey = properties.getProperty("GEMINI_API_KEY").orEmpty()
        }

        // If local.properties key is missing, you can paste it here to test:
        if (apiKey.isBlank()) {
            apiKey = "YOUR_API_KEY_HERE"
        }

        println("=========================================")
        println("Testing Gemini API with key: ${apiKey.take(8)}...")
        println("=========================================")

        if (apiKey.isBlank() || apiKey == "YOUR_API_KEY_HERE") {
            println("ERROR: Please provide a valid Gemini API key in local.properties or paste it in GeminiApiTest.kt")
            return
        }

        // Test List Models
        println("=========================================")
        println("Listing available models for the key:")
        println("=========================================")
        try {
            val listUrl = URL("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey")
            val listConn = listUrl.openConnection() as HttpURLConnection
            listConn.requestMethod = "GET"
            listConn.setRequestProperty("Content-Type", "application/json")
            
            val listCode = listConn.responseCode
            println("List Models HTTP Response Code: $listCode")
            
            val listStream = if (listCode in 200..299) listConn.inputStream else listConn.errorStream
            val listResponse = listStream?.bufferedReader()?.use { it.readText() } ?: "No response"
            println(listResponse)
        } catch (e: Exception) {
            println("Exception listing models:")
            e.printStackTrace()
        }
        println("=========================================")
    }

    @Test
    fun testGenerateContent() {
        var apiKey = ""
        val properties = Properties()
        val localPropertiesFile = File("../local.properties")
        if (localPropertiesFile.exists()) {
            localPropertiesFile.inputStream().use { properties.load(it) }
            apiKey = properties.getProperty("GEMINI_API_KEY").orEmpty()
        }

        if (apiKey.isBlank()) {
            apiKey = "YOUR_API_KEY_HERE"
        }

        if (apiKey.isBlank() || apiKey == "YOUR_API_KEY_HERE") {
            println("ERROR: Please provide a valid Gemini API key")
            return
        }

        val modelsToTest = listOf(
            "gemini-2.0-flash",
            "gemini-2.5-flash",
            "gemini-flash-latest"
        )

        for (model in modelsToTest) {
            println("\n=========================================")
            println("Testing generateContent with model: $model")
            println("=========================================")
            try {
                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                val requestBody = """
                    {
                      "contents": [{
                        "parts":[{
                          "text": "Hello, this is a short API test. Reply with a short message."
                        }]
                      }]
                    }
                """.trimIndent()

                OutputStreamWriter(conn.outputStream).use { it.write(requestBody) }

                val code = conn.responseCode
                println("HTTP Response Code: $code")
                
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val response = stream?.bufferedReader()?.use { it.readText() } ?: "No response"
                println("Response:")
                println(response)
            } catch (e: Exception) {
                println("Exception testing model $model:")
                e.printStackTrace()
            }
        }
    }
}

