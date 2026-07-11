Feature: Media Library - Image Upload and Management

  Background:
    Given the user is authenticated with verified email
    And the user is on the media library page

  @real-cas @media
  Scenario: Upload fresh image content
    Given the user selects a new image file
    When the user uploads the file
    Then a PUT request should be sent with status 201
    And a POST request to "/upload" should be sent with status 200
    And an asset card should appear with status "READY"

  @real-cas @media
  Scenario: Upload exact duplicate image is deduplicated
    Given an image with hash "ABC123" already exists
    When the user uploads a file with the same hash "ABC123"
    Then the image should appear only once in the library
    And the visible card count should not increase

  @real-cas @media
  Scenario: Upload duplicate shows existing image as READY
    Given an image is already uploaded
    When the user uploads the same image again
    Then the image should appear only once in the library
    And the card status should display "READY"

  @real-cas @media
  Scenario: Image card shows READY status
    Given an image is uploaded
    When the upload process completes
    Then the card status should display "READY"

  @e2e @media
  Scenario: Media selector is accessible from composer
    Given the user is on the compose modal
    Then a media/image selector button should be visible

  @e2e @media
  Scenario: Selected image appears embedded in composer
    Given the user is on the compose modal
    When the user selects an image from the media library
    Then the image should appear embedded in the composer

  @e2e @media
  Scenario: LinkedIn preview shows embedded image
    Given the user has selected an image in the composer
    Then the LinkedIn preview should display the selected image

  @e2e @media
  Scenario: Post with image saves correctly
    Given the user is on the compose modal
    And the user fills the content with "Post with image"
    And the user selects an image from the media library
    And the user selects a channel
    And the user sets a future date and time
    When the user clicks the submit button
    Then the post should be created with the image reference

  @e2e @media
  Scenario: Editing post with image shows precached image
    Given there is a post with an embedded image
    When the user clicks on the post card
    And the user clicks the edit button
    Then the composer should show the precached image