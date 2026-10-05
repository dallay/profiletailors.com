package com.profiletailors.smp.shortlinks.infrastructure.codegen

import com.profiletailors.smp.shortlinks.domain.ShortCode
import com.profiletailors.smp.shortlinks.domain.ShortCodeGenerator
import org.springframework.stereotype.Component
import java.security.SecureRandom

@Component
class SecureRandomShortCodeGenerator : ShortCodeGenerator {
    private val secureRandom = SecureRandom()

    override fun generate(): ShortCode {
        val code = CharArray(ShortCode.DEFAULT_LENGTH) {
            ShortCode.BASE62_ALPHABET[secureRandom.nextInt(ShortCode.BASE62_ALPHABET.size)]
        }
        return ShortCode(String(code))
    }
}
