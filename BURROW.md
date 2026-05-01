# Burrow
This is a simple writeup on what the burrow actually does. Sorry about all the typos and stuff

In Minecraft, there is a "funny value" that is exactly `1e-7`. It's probably used in the minecraft collision checks as leniency.

We can abuse this leniency by getting ourselves into a position of `1 - (1e-7)`. For example: clip up `0.99999999` blocks, place block, rubberband and profit. This works on most NCP servers.

Now the method to not get flagged from clipping used in tar-addon is as following: we take a starting velocity of `velocity`. Now we simulate jumping (through packets) by looping this code x times: `yPos = yPos + velocity; velocity = (velocity - 0.08) * 0.98`. `0.08` and `0.98` are minecraft constants for gravity. With this, we can simulate a jump.

How do we get the starting velocity to get to `1 - (1e-7)`? 

We use math to find the correct velocity for each `loop` value by using this simple "formula":
```
A = 0.08
B = 0.98
C = 0.9999999
```
For a single iteration, of course the starting velocity is just `C`.
For the second iteration, starting velocity is `(C+AB)/(1+B)`
For the third iteration, starting velocity is `(C+AB²+2AB)/(1+B+B²)`
For the fourth iteration, starting velocity is `(C+AB³+2AB²+3AB)/(1+B+B²+B³)`

You can start to see a pattern, adjust C for the block height you are using (`blockHeight - 1e-7`)

Third iteration gives us a starting velocity of `0.419545636648075`. This is very close to the normal minecraft jump height (around `0.42`). By having 4 iterations, we only send 4 packets and have an exteremely vanilla-like jump to the correct height.

EDIT: works on cc as of 1/05/2026
