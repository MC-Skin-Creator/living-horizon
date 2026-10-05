# Five times a second: every player's position, published.
execute as @a at @s run function farfarplayer:publish
data remove storage farfarplayer:tmp e
scoreboard players add #clock ffp.m 1
# Mobs move slowly and there can be hundreds: once every 25 runs, five seconds.
scoreboard players add #mobtick ffp.t 1
execute if score #mobtick ffp.t matches 25.. run function farfarplayer:mobs
schedule function farfarplayer:update 4t replace
