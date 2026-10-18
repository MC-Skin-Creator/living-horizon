# Every five seconds: every remembered mob loaded around a player, published.
scoreboard players set #mobtick lh.t 0
# A chest minecart nobody ever opened was left by a mineshaft, under the ground: never shared.
execute as @e[type=minecraft:chest_minecart,tag=!lh.kind,tag=!lh.buried] if data entity @s LootTable run tag @s add lh.buried
execute as @e[type=#livinghorizon:remembered,tag=!lh.buried] at @s run function livinghorizon:mob
data remove storage livinghorizon:tmp e
