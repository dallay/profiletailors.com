package com.profiletailors.smp.publishing.application

class PublicationNotFoundException(publicationId: String) :
    IllegalArgumentException("Publication '$publicationId' was not found in the active workspace.")

class SocialAccountNotFoundException(socialAccountId: String) :
    IllegalArgumentException("Social account '$socialAccountId' was not found in the active workspace.")

class RecurringScheduleNotFoundException(scheduleId: String) :
    IllegalArgumentException("Recurring schedule '$scheduleId' was not found in the active workspace.")
