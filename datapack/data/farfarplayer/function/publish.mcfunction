# Run as and at one player.
# One copy of the player's data, read three times: each "data get entity" would
# serialise the whole player, inventory included.
data modify storage farfarplayer:tmp e set from entity @s
execute store result score @s ffp.x run data get storage farfarplayer:tmp e.Pos[0] 10
execute store result score @s ffp.y run data get storage farfarplayer:tmp e.Pos[1] 10
execute store result score @s ffp.z run data get storage farfarplayer:tmp e.Pos[2] 10

# ffp.m is built in the scratch objective and copied once, so that clients never
# receive a half-built value.
execute store result score @s ffp.t run data get storage farfarplayer:tmp e.Rotation[0]
scoreboard players operation @s ffp.t %= #360 ffp.t
execute if dimension minecraft:the_nether run scoreboard players add @s ffp.t 360
execute if dimension minecraft:the_end run scoreboard players add @s ffp.t 720
execute unless dimension minecraft:overworld unless dimension minecraft:the_nether unless dimension minecraft:the_end run scoreboard players add @s ffp.t 1080
execute if predicate farfarplayer:riding run scoreboard players add @s ffp.t 1440
scoreboard players operation @s ffp.m = @s ffp.t
