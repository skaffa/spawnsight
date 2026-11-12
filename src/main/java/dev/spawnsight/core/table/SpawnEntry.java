package dev.spawnsight.core.table;

/** One weighted entry of a spawn list ({@code MobSpawnSettings.SpawnerData} plus its weight). */
public record SpawnEntry(String type, int weight, int minCount, int maxCount) {
}
