# Removes everything this pack created. Then: /datapack disable "file/living-horizon"
schedule clear livinghorizon:update
scoreboard objectives remove lh.x
scoreboard objectives remove lh.y
scoreboard objectives remove lh.z
scoreboard objectives remove lh.m
scoreboard objectives remove lh.t
scoreboard objectives remove lh.k
data remove storage livinghorizon:tmp e
