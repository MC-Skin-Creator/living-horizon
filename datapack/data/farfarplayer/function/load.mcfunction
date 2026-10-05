# Far Far Player - shares every player's position through the scoreboard.
#
# An objective shown in any display slot is sent by the server to every client, at any
# distance. These four are shown in the sidebars of team colours nobody plays in, so
# nobody sees them on screen, and the mod reads them from the scoreboard it receives.
#
#   ffp.x, ffp.y, ffp.z   position, in tenths of a block
#   ffp.m                 yaw (0-359) + 360 * dimension (0 overworld, 1 nether, 2 end, 3 other)
#                         + 1440 if riding something
#   #clock ffp.m          counts up while this pack runs; the mod stops trusting the
#                         scores when it stops
#   #version ffp.m        the layout above, for the mod to check
#
# Remembered mobs (animals, villagers, golems: tags/entity_type/remembered.json) are
# published in the same four objectives under their UUID; see mob.mcfunction.
# Boats too: a parked boat vanishes from far away just like an animal.
#
# Anyone in a team of one of these colours would see the coordinates in their sidebar.
# If your server uses them, change the four setdisplay lines below.

scoreboard objectives add ffp.x dummy
scoreboard objectives add ffp.y dummy
scoreboard objectives add ffp.z dummy
scoreboard objectives add ffp.m dummy
# Scratch objective, never displayed, so never sent.
scoreboard objectives add ffp.t dummy
# A mob's kind, worked out once. Not displayed either.
scoreboard objectives add ffp.k dummy

scoreboard objectives setdisplay sidebar.team.black ffp.x
scoreboard objectives setdisplay sidebar.team.dark_blue ffp.y
scoreboard objectives setdisplay sidebar.team.dark_green ffp.z
scoreboard objectives setdisplay sidebar.team.dark_aqua ffp.m

scoreboard players set #360 ffp.t 360
scoreboard players set #2048 ffp.t 2048
scoreboard players set #version ffp.m 1

schedule function farfarplayer:update 1t replace
