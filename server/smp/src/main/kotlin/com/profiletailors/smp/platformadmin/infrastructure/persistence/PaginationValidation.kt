package com.profiletailors.smp.platformadmin.infrastructure.persistence

const val ADMIN_PAGE_MAX_SIZE = 100

fun validatePagination(page: Int, size: Int) {
    require(page >= 0) { "Page must be non-negative" }
    require(size in 1..ADMIN_PAGE_MAX_SIZE) { "Page size must be between 1 and $ADMIN_PAGE_MAX_SIZE" }
}
