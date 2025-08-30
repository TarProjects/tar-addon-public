# AutoSpectator

Small writeup of a funny "exploit" found on crystalpvp.cc

One time I was spectating matches and suddenly got into spectator on spawn. I noticed that i /spectated the duel just a tiny bit before it ended. I went to retest and surely enough it could be replicated.

The timing window is actually pretty large, since cc only puts you into spectator around 10-15 ticks after you start spectating.

Note that you have to spectate the winner of the duel (since the player who lost the duel is already in spawn). However, the winner of the duel stays in the duel arena for around 4-5 seconds.

I made a module that uses regex to match "duel end" messages and /spectates the winner after 90-95 ticks. This makes you go into spectator 100% of the time.

You can even exit spectator by doing /kit above the Y threshold which is around 60-100. Using /kill does not make you exit spectator.

There is a second way to exit spectator, which is by moving and teleporting to a person in a duel using the spectator hotbar. For some reason, this sets you into survival ANYWHERE (including below the ground, in phase etc.)

Probably will be patched soon, use it while you can
