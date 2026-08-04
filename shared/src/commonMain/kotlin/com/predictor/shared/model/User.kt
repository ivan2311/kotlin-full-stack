package com.predictor.shared.model

import kotlinx.serialization.Serializable

/**
 * A person playing the prediction game. Authentication is deliberately out of scope
 * for this first iteration — a user is just an identity to attach predictions and
 * points to.
 */
@Serializable
data class User(
    val id: String,
    val displayName: String,
)
