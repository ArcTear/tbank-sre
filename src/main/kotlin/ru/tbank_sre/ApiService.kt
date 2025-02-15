package ru.tbank_sre

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import kotlin.math.pow

@Service
class ApiService(private val webClient: WebClient) {
    private val logger = LoggerFactory.getLogger(ApiService::class.java)

    fun getData(url: String): String {
        val maxRetries = 3

        for (attempt in 0 until maxRetries) {
            try {
                val response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String::class.java)
                    .block()

                return response ?: throw RuntimeException("Empty response body")
            } catch (ex: WebClientResponseException) {
                if (ex.statusCode in listOf(HttpStatus.INTERNAL_SERVER_ERROR, HttpStatus.BAD_GATEWAY, HttpStatus.SERVICE_UNAVAILABLE, HttpStatus.GATEWAY_TIMEOUT)) {
                    if (attempt < maxRetries - 1) {
                        val delayTime = 2.0.pow(attempt).toLong() * 1000
                        logger.warn("Request failed with status ${ex.statusCode}. Retrying in ${delayTime / 1000} seconds...")
                        runBlocking { delay(delayTime) }
                    } else {
                        logger.error("Max retries reached. Giving up.")
                        throw ex
                    }
                } else {
                    throw ex
                }
            }
        }
        throw RuntimeException("Failed to fetch data after $maxRetries attempts")
    }
}
