package com.profiletailors.spring.boot.presentation.pagination

import com.profiletailors.common.domain.presentation.pagination.OffsetPageResponse
import com.profiletailors.spring.boot.presentation.ResponseBodyResultHandler
import org.springframework.http.codec.ServerCodecConfigurer
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.reactive.accept.RequestedContentTypeResolver

@ControllerAdvice
class OffsetPageResponseHandler(
    serverCodecConfigurer: ServerCodecConfigurer,
    resolver: RequestedContentTypeResolver,
    presenter: OffsetPagePresenter,
) : ResponseBodyResultHandler<OffsetPageResponse<*>>(
    serverCodecConfigurer.writers,
    resolver,
    presenter,
)
