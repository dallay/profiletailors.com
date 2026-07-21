export default {
  takedown: {
    report: {
      action: 'Report Copyright Issue',
      title: 'Report Copyright Issue',
      description:
        'Submit a takedown report for this media asset. Our team will review your report and may suspend the content if it violates our copyright policy.',
      reasonLabel: 'Reason for Report',
      reasonPlaceholder:
        'Describe why this content should be removed (e.g., unauthorized use of your work, trademark violation, etc.)',
      emailLabel: 'Your Email',
      emailHint: 'This email will be used to contact you about your report.',
      urlLabel: 'Reference URL (optional)',
      urlPlaceholder: 'https://example.com/original-work',
      submitAction: 'Submit Report',
      submitting: 'Submitting…',
      errors: {
        reasonRequired: 'Please provide a reason for your report.',
        submitFailed: 'Failed to submit report. Please try again.',
      },
    },
    review: {
      title: 'Takedown Reports',
      subtitle: 'Review and act on copyright reports submitted by users.',
      statusFilter: 'Filter by status',
      filterAll: 'All',
      statusReported: 'Reported',
      statusApproved: 'Approved',
      statusDismissed: 'Dismissed',
      refresh: 'Refresh',
      loading: 'Loading reports…',
      empty: 'No reports found.',
      emptyHint: 'No takedown reports match the current filter.',
      approveAction: 'Approve',
      rejectAction: 'Reject',
      rejectionReason: 'Rejection Reason',
      errors: {
        loadFailed: 'Failed to load reports.',
        approveFailed: 'Failed to approve report.',
        rejectFailed: 'Failed to reject report.',
      },
      rejectDialog: {
        title: 'Reject Report',
        description:
          'Provide a reason for rejecting this takedown report. The reporter will be notified.',
        reasonLabel: 'Rejection Reason',
        reasonPlaceholder: 'Explain why this report is being rejected…',
      },
    },
  },
}
