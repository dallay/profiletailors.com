package com.profiletailors.smp.mediaprovider.unsplash

interface UnsplashClient {
    suspend fun searchPhotos(query: String, page: Int): UnsplashSearchResponse
    suspend fun getPhoto(photoId: String): UnsplashPhoto
    suspend fun downloadPhoto(photo: UnsplashPhoto): UnsplashBinary
}
