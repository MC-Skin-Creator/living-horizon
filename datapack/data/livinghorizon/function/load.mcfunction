# Living Horizon - shares every player's position through the scoreboard.
#
# An objective shown in any display slot is sent by the server to every client, at any
# distance. These four are shown in the sidebars of team colours nobody plays in, so
# nobody sees them on screen, and the mod reads them from the scoreboard it receives.
#
#   lh.x, lh.y, lh.z   position, in tenths of a block
#   lh.m                 yaw (0-359) + 360 * dimension (0 overworld, 1 nether, 2 end, 3 other)
#                         + 1440 if riding something
#   #clock lh.m          counts up while this pack runs; the mod stops trusting the
#                         scores when it stops
#   #version lh.m        the layout above, for the mod to check
#
# Remembered mobs (animals, villagers, golems: tags/entity_type/remembered.json) are
# published in the same four objectives under their UUID; see mob.mcfunction.
# Boats too: a parked boat vanishes from far away just like an animal.
#
# Anyone in a team of one of these colours would see the coordinates in their sidebar.
# If your server uses them, change the four setdisplay lines below.

scoreboard objectives add lh.x dummy
scoreboard objectives add lh.y dummy
scoreboard objectives add lh.z dummy
scoreboard objectives add lh.m dummy
# Scratch objective, never displayed, so never sent.
scoreboard objectives add lh.t dummy
# A mob's kind, worked out once. Not displayed either.
scoreboard objectives add lh.k dummy

scoreboard objectives setdisplay sidebar.team.black lh.x
scoreboard objectives setdisplay sidebar.team.dark_blue lh.y
scoreboard objectives setdisplay sidebar.team.dark_green lh.z
scoreboard objectives setdisplay sidebar.team.dark_aqua lh.m

scoreboard players set #360 lh.t 360
scoreboard players set #2048 lh.t 2048
scoreboard players set #version lh.m 1

schedule function livinghorizon:update 1t replace
