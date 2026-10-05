package com.profiletailors.smp.shortlinks.application

sealed class ShortLinksApplicationException(message: String) : RuntimeException(message)

class LinkNotFoundApplicationException(val linkId: String) :
    ShortLinksApplicationException("Link not found: $linkId")

class LinkExpiredApplicationException(val linkId: String) :
    ShortLinksApplicationException("Link has expired: $linkId")

class LinkDisabledApplicationException(val linkId: String) :
    ShortLinksApplicationException("Link is disabled: $linkId")

class LinkQuarantinedApplicationException(val linkId: String) :
    ShortLinksApplicationException("Link is quarantined: $linkId")

class LinkDeletedApplicationException(val linkId: String) :
    ShortLinksApplicationException("Link has been deleted: $linkId")
