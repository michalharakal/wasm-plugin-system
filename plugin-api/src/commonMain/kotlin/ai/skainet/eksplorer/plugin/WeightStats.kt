package ai.skainet.eksplorer.plugin

import kotlinx.serialization.Serializable

@Serializable
data class WeightStats(
    val mean: Double = 0.0,
    val std: Double = 0.0,
    val min: Double = 0.0,
    val max: Double = 0.0,
    val sparsity: Double = 0.0,
)
