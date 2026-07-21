@media @smoke @fast
Feature: Media asset management
  Media Assets domain: register, retrieve, list, and delete media assets.

  Background:
    Given an authorized workspace member exists

  Scenario: Register a media asset via CAS PUT
    When the client registers a media asset with file hash "b591d9820ae723ef0604a2014276dea6a9a26566b5f857a146a51fae9b22da41" size 12345 and type "image/jpeg"
    Then the media response status should be 201
    And the response should contain an assetId
    And the asset status should be "PENDING_UPLOAD"

  Scenario: Get a media asset by ID
    Given a media asset exists
    When the client requests the media asset
    Then the media response status should be 200
    And the response should contain an assetId
    And the response should contain the media type "image/png"

  Scenario: List media assets
    Given a media asset exists
    When the client lists media assets
    Then the media response status should be 200
    And the response should contain 1 asset

  Scenario: Delete a media asset
    Given a media asset exists
    When the client deletes the media asset
    Then the media response status should be 200
    And the asset should be marked as deleted
    And the media asset should be deleted from the database
