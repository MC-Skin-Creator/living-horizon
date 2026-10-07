# Five times a second: every player's position, published.
execute as @a at @s run function livinghorizon:publish
data remove storage livinghorizon:tmp e
scoreboard players add #clock lh.m 1
# Mobs move slowly and there can be hundreds: once every 25 runs, five seconds.
scoreboard players add #mobtick lh.t 1
execute if score #mobtick lh.t matches 25.. run function livinghorizon:mobs
schedule function livinghorizon:update 4t replace
