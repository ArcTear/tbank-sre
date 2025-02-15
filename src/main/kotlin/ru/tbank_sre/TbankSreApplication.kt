package ru.tbank_sre

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class TbankSreApplication

fun main(args: Array<String>) {
	runApplication<TbankSreApplication>(*args)
}
