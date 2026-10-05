# Every five seconds: every remembered mob loaded around a player, published.
scoreboard players set #mobtick ffp.t 0
execute as @e[type=#farfarplayer:remembered] at @s run function farfarplayer:mob
data remove storage farfarplayer:tmp e
