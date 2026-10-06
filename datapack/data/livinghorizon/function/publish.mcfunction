# Run as and at one player.
# One copy of the player's data, read three times: each "data get entity" would
# serialise the whole player, inventory included.
data modify storage livinghorizon:tmp e set from entity @s
execute store result score @s lh.x run data get storage livinghorizon:tmp e.Pos[0] 10
execute store result score @s lh.y run data get storage livinghorizon:tmp e.Pos[1] 10
execute store result score @s lh.z run data get storage livinghorizon:tmp e.Pos[2] 10

# lh.m is built in the scratch objective and copied once, so that clients never
# receive a half-built value.
execute store result score @s lh.t run data get storage livinghorizon:tmp e.Rotation[0]
scoreboard players operation @s lh.t %= #360 lh.t
execute if dimension minecraft:the_nether run scoreboard players add @s lh.t 360
execute if dimension minecraft:the_end run scoreboard players add @s lh.t 720
execute unless dimension minecraft:overworld unless dimension minecraft:the_nether unless dimension minecraft:the_end run scoreboard players add @s lh.t 1080
execute if predicate livinghorizon:riding run scoreboard players add @s lh.t 1440
scoreboard players operation @s lh.m = @s lh.t
