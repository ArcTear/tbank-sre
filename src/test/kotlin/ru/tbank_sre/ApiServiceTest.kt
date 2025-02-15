package ru.tbank_sre

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.*
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono

@ExtendWith(SpringExtension::class)
@SpringBootTest
class ApiServiceTest {
    private val webClient: WebClient = mock(WebClient::class.java)
    private val requestHeadersUriSpec = mock(WebClient.RequestHeadersUriSpec::class.java)
    private val requestHeadersSpec = mock(WebClient.RequestHeadersSpec::class.java)
    private val responseSpec = mock(WebClient.ResponseSpec::class.java)

    init {
        `when`(webClient.get()).thenReturn(requestHeadersUriSpec)
        `when`(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec)
        `when`(requestHeadersSpec.retrieve()).thenReturn(responseSpec)
    }

    @Test
    fun `test circuit breaker transitions`() {
        val apiService = CircuitBreakerApiService(webClient)

        `when`(responseSpec.bodyToMono(String::class.java))
            .thenReturn(
                Mono.error(
                    WebClientResponseException(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "Error",
                        null,
                        null,
                        null
                    )
                )
            )
            .thenReturn(
                Mono.error(
                    WebClientResponseException(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "Error",
                        null,
                        null,
                        null
                    )
                )
            )
            .thenReturn(
                Mono.error(
                    WebClientResponseException(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "Error",
                        null,
                        null,
                        null
                    )
                )
            )
            .thenReturn(Mono.just("Recovered Response"))

        repeat(3) {
            assertThrows(WebClientResponseException::class.java) {
                apiService.getDataWithCircuitBreaker("http://example.com")
            }
        }

        Thread.sleep(11000) // Wait for circuit breaker timeout

        val result = apiService.getDataWithCircuitBreaker("http://example.com")
        assertEquals("Recovered Response", result)
    }

    @Test
    fun `test circuit breaker remains open on failure after half-open`() {
        val apiService = CircuitBreakerApiService(webClient)

        `when`(responseSpec.bodyToMono(String::class.java))
            .thenReturn(
                Mono.error(
                    WebClientResponseException(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "Error",
                        null,
                        null,
                        null
                    )
                )
            )

        repeat(3) {
            assertThrows(WebClientResponseException::class.java) {
                apiService.getDataWithCircuitBreaker("http://example.com")
            }
        }

        Thread.sleep(11000) // Wait for circuit breaker timeout

        assertThrows(WebClientResponseException::class.java) {
            apiService.getDataWithCircuitBreaker("http://example.com")
        }
    }

    @Test
    fun `test retry logic`() {
        val apiService = ApiService(webClient)

        `when`(responseSpec.bodyToMono(String::class.java))
            .thenReturn(
                Mono.error(
                    WebClientResponseException(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "Error",
                        null,
                        null,
                        null
                    )
                )
            )
            .thenReturn(Mono.just("Recovered Response"))

        val result = apiService.getData("http://example.com")
        assertEquals("Recovered Response", result)
    }

    @Test
    fun `test successful request`() {
        val mockResponse = "Success Response"

        `when`(responseSpec.bodyToMono(String::class.java)).thenReturn(Mono.just(mockResponse))

        val apiService = ApiService(webClient)
        val result = apiService.getData("http://example.com")

        assertEquals(mockResponse, result)
    }

    @Test
    fun `test request with retries and failure`() {
        `when`(responseSpec.bodyToMono(String::class.java)).thenReturn(
            Mono.error(WebClientResponseException(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Error", null, null, null))
        )

        val apiService = ApiService(webClient)
        assertThrows(WebClientResponseException::class.java) {
            apiService.getData("http://example.com")
        }
    }

    @Test
    fun `test request with one retry and success`() {
        `when`(responseSpec.bodyToMono(String::class.java))
            .thenReturn(
                Mono.error(
                    WebClientResponseException(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "Error",
                        null,
                        null,
                        null
                    )
                )
            )
            .thenReturn(Mono.just("Recovered Response"))

        val apiService = ApiService(webClient)
        val result = apiService.getData("http://example.com")

        assertEquals("Recovered Response", result)
    }
}