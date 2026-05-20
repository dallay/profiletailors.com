package com.profiletailors.common.domain.presentation

import com.profiletailors.common.domain.bus.query.Response

open class PageResponse<T>(
    open val data: Collection<T>
) : Response
