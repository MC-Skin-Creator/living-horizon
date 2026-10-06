# Every five seconds: every remembered mob loaded around a player, published.
scoreboard players set #mobtick lh.t 0
execute as @e[type=#livinghorizon:remembered] at @s run function livinghorizon:mob
data remove storage livinghorizon:tmp e
