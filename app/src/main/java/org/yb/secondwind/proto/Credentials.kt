package org.yb.secondwind.proto

/** 4-character ASCII credential. docs/PROTOCOL.md §3. */
@JvmInline
value class Keyword(val bytes: ByteArray) {
    init { require(bytes.size == 4) { "keyword must be 4 ASCII characters" } }
    companion object {
        fun of(s: String) = Keyword(s.toByteArray(Charsets.US_ASCII))
        fun isValid(s: String) = s.length == 4 && s.all { it.code in 0x20..0x7E }
    }
}

@JvmInline
value class Password(val bytes: ByteArray) {
    init { require(bytes.size == 4) { "password must be 4 ASCII characters" } }
    companion object {
        fun of(s: String) = Password(s.toByteArray(Charsets.US_ASCII))
        fun isValid(s: String) = Keyword.isValid(s)
    }
}

class Credentials(val keyword: Keyword, val password: Password) {
    companion object {
        fun of(keyword: String, password: String) = Credentials(Keyword.of(keyword), Password.of(password))
    }
}
