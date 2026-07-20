package com.profiletailors.smp.privacy.infrastructure.http

/**
 * Typed payload for the `result` field of [PrivacyRequestStatusResponseDto].
 *
 * Today the response payload is a single pointer to a downloadable URL or to an
 * inlined JSON document. The DTO used to be `Map<String, Any?>`; this typed
 * alternative makes the contract explicit and removes a typing hole.
 *
 * @property ref a URL or pointer to where the assembled data subject response is
 *               stored. Null when the request has not produced a result yet.
 */
data class PrivacyRequestResult(val ref: String?)
