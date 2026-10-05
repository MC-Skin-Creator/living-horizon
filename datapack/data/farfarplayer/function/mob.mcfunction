# Run as and at one mob. Its scores are kept while its chunk is unloaded, so the last
# player to have been near it leaves its position for everyone; the server resets them
# when it dies.
#
#   ffp.x, ffp.y, ffp.z   position, in tenths of a block
#   ffp.m                 kind (1-255, see mob_kind) + 256 * dimension + 1024 if a baby
#                         + 2048 * variant (horse, llama, axolotl, parrot: Variant;
#                         sheep: Color; rabbit: RabbitType)
execute unless entity @s[tag=ffp.kind] run function farfarplayer:mob_kind
data modify storage farfarplayer:tmp e set from entity @s
execute store result score @s ffp.x run data get storage farfarplayer:tmp e.Pos[0] 10
execute store result score @s ffp.y run data get storage farfarplayer:tmp e.Pos[1] 10
execute store result score @s ffp.z run data get storage farfarplayer:tmp e.Pos[2] 10

scoreboard players set @s ffp.t 0
execute if entity @s[type=minecraft:horse] store result score @s ffp.t run data get storage farfarplayer:tmp e.Variant
execute if entity @s[type=minecraft:llama] store result score @s ffp.t run data get storage farfarplayer:tmp e.Variant
execute if entity @s[type=minecraft:axolotl] store result score @s ffp.t run data get storage farfarplayer:tmp e.Variant
execute if entity @s[type=minecraft:parrot] store result score @s ffp.t run data get storage farfarplayer:tmp e.Variant
execute if entity @s[type=minecraft:sheep] store result score @s ffp.t run data get storage farfarplayer:tmp e.Color
execute if entity @s[type=minecraft:rabbit] store result score @s ffp.t run data get storage farfarplayer:tmp e.RabbitType
scoreboard players operation @s ffp.t %= #2048 ffp.t
scoreboard players operation @s ffp.t *= #2048 ffp.t

execute store result score #age ffp.t run data get storage farfarplayer:tmp e.Age
execute if score #age ffp.t matches ..-1 run scoreboard players add @s ffp.t 1024
execute if dimension minecraft:the_nether run scoreboard players add @s ffp.t 256
execute if dimension minecraft:the_end run scoreboard players add @s ffp.t 512
execute unless dimension minecraft:overworld unless dimension minecraft:the_nether unless dimension minecraft:the_end run scoreboard players add @s ffp.t 768
scoreboard players operation @s ffp.t += @s ffp.k
scoreboard players operation @s ffp.m = @s ffp.t
