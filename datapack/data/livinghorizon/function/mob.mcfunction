# Run as and at one mob. Its scores are kept while its chunk is unloaded, so the last
# player to have been near it leaves its position for everyone; the server resets them
# when it dies.
#
#   lh.x, lh.y, lh.z   position, in tenths of a block
#   lh.m                 kind (1-255, see mob_kind) + 256 * dimension + 1024 if a baby
#                         + 2048 * variant (horse, llama, axolotl, parrot: Variant;
#                         sheep: Color; rabbit: RabbitType)
execute unless entity @s[tag=lh.kind] run function livinghorizon:mob_kind
data modify storage livinghorizon:tmp e set from entity @s
execute store result score @s lh.x run data get storage livinghorizon:tmp e.Pos[0] 10
execute store result score @s lh.y run data get storage livinghorizon:tmp e.Pos[1] 10
execute store result score @s lh.z run data get storage livinghorizon:tmp e.Pos[2] 10

scoreboard players set @s lh.t 0
execute if entity @s[type=minecraft:horse] store result score @s lh.t run data get storage livinghorizon:tmp e.Variant
execute if entity @s[type=minecraft:llama] store result score @s lh.t run data get storage livinghorizon:tmp e.Variant
execute if entity @s[type=minecraft:axolotl] store result score @s lh.t run data get storage livinghorizon:tmp e.Variant
execute if entity @s[type=minecraft:parrot] store result score @s lh.t run data get storage livinghorizon:tmp e.Variant
execute if entity @s[type=minecraft:sheep] store result score @s lh.t run data get storage livinghorizon:tmp e.Color
execute if entity @s[type=minecraft:rabbit] store result score @s lh.t run data get storage livinghorizon:tmp e.RabbitType
scoreboard players operation @s lh.t %= #2048 lh.t
scoreboard players operation @s lh.t *= #2048 lh.t

execute store result score #age lh.t run data get storage livinghorizon:tmp e.Age
execute if score #age lh.t matches ..-1 run scoreboard players add @s lh.t 1024
execute if dimension minecraft:the_nether run scoreboard players add @s lh.t 256
execute if dimension minecraft:the_end run scoreboard players add @s lh.t 512
execute unless dimension minecraft:overworld unless dimension minecraft:the_nether unless dimension minecraft:the_end run scoreboard players add @s lh.t 768
scoreboard players operation @s lh.t += @s lh.k
scoreboard players operation @s lh.m = @s lh.t
