package com.profiletailors.smp.governance.application

/**
 * Thrown when a [TakedownReport] is not found within the current workspace.
 */
class TakedownReportNotFoundException(reportId: String) :
    IllegalArgumentException("Takedown report not found: $reportId")
