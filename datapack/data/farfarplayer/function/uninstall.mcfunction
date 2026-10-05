# Removes everything this pack created. Then: /datapack disable "file/far-far-player"
schedule clear farfarplayer:update
scoreboard objectives remove ffp.x
scoreboard objectives remove ffp.y
scoreboard objectives remove ffp.z
scoreboard objectives remove ffp.m
scoreboard objectives remove ffp.t
scoreboard objectives remove ffp.k
data remove storage farfarplayer:tmp e
