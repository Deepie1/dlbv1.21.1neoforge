# Remet a zero la progression Deep Lucky Block du joueur qui execute la fonction
# Usage en jeu : /function deep_lucky_block:resets
# ATTENTION : pas de "/" devant les commandes dans un .mcfunction, et "#" pour les commentaires !

# Compteur de blocs casses
data modify entity @s ForgeData.slb_deep_lucky_blocks_broken set value 0

# Bonus d'enchantements desactive
data modify entity @s ForgeData.slb_enchant_bonus_active set value 0b

# Milestones reinitialises
data remove entity @s ForgeData.slb_milestone_achieved_10
data remove entity @s ForgeData.slb_milestone_achieved_50
data remove entity @s ForgeData.slb_milestone_achieved_100
data remove entity @s ForgeData.slb_milestone_achieved_500
data remove entity @s ForgeData.slb_milestone_achieved_1000

# Advancements revoques
advancement revoke @s only deep_lucky_block:root
advancement revoke @s only deep_lucky_block:deep_lucky_block_broken
advancement revoke @s only deep_lucky_block:negative_streak
advancement revoke @s only deep_lucky_block:milestone_10
advancement revoke @s only deep_lucky_block:milestone_50
advancement revoke @s only deep_lucky_block:milestone_100
advancement revoke @s only deep_lucky_block:milestone_500
advancement revoke @s only deep_lucky_block:milestone_1000
